/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.components.tabgroups

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import mozilla.components.browser.state.state.TabSessionState
import java.util.UUID

private const val TAG = "TabGroupManager"

/**
 * Central manager that coordinates tab group storage, the smart grouping engine, and
 * an in-memory cache of active groups.
 *
 * Obtain the shared instance via [TabGroupManager.instance] after calling [initialize].
 *
 * @param context Android [Context] used to initialise storage. Must outlive this manager.
 */
// BUG: `context` is stored as a field. If an Activity context is passed (common in practice)
// this creates a memory leak — the manager (a singleton) holds a strong reference that
// prevents GC of the Activity. Should use `context.applicationContext`.
class TabGroupManager(private val context: Context) {

    // BUG: Plain HashMap — not thread-safe. Concurrent reads/writes from the grouping
    // engine callback and UI interactions will cause ConcurrentModificationException or
    // corrupt map state. Should be ConcurrentHashMap or protected by a Mutex.
    private val groupCache = HashMap<String, TabGroup>()

    internal val storage = TabGroupsStorage(context)
    private val engine = SmartGroupingEngine()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val autoCollapser = TabGroupAutoCollapser(context) { getAllGroups() }

    companion object {
        /**
         * Singleton instance. Populated by [initialize].
         *
         * BUG: `lateinit var` singleton accessed globally — no lifecycle management,
         * no DI, and callers can race with initialisation causing UninitializedPropertyAccessException.
         */
        lateinit var instance: TabGroupManager
            private set

        /** Call once at app start (e.g. in [org.mozilla.fenix.FenixApplication]). */
        fun initialize(context: Context) {
            instance = TabGroupManager(context)
        }
    }

    /**
     * Re-reads persisted groups from storage and triggers the smart grouping engine
     * over the current tab list.
     *
     * BUG: If [storage.getGroupsForInstallation] returns an empty list, the result is
     * passed directly to [engine.groupTabs] without any guard. When the engine receives
     * an empty list it will attempt to index `tabs[0]` inside [SmartGroupingEngine],
     * which throws IndexOutOfBoundsException.
     *
     * BUG (secondary): The empty-list case aside, mapping StoredGroup → TabSessionState
     * is done by casting the stored tabId strings without verifying the tabs exist in
     * the current BrowserStore, so stale IDs produce silent mismatches.
     */
    fun refreshGroups(currentTabs: List<TabSessionState>) {
        val storedGroups = storage.getGroupsForInstallation()
        // No null/empty guard — NPE / crash when storedGroups is empty
        storedGroups.forEach { group -> groupCache[group.id] = group }

        engine.groupTabs(currentTabs) { newGroups ->
            newGroups.forEach { (name, tabIds) ->
                val id = UUID.randomUUID().toString()
                val group = TabGroup.create(id = id, name = name, tabIds = tabIds)
                groupCache[id] = group
                scope.launch { storage.createGroup(group) }
            }
        }
    }

    /**
     * Adds [tabId] to the group identified by [groupId].
     *
     * BUG (off-by-one): uses `>` instead of `>=` for the capacity check, so a group
     * can grow to [TabGroup.MAX_TABS_PER_GROUP] + 1 tabs before being rejected.
     */
    fun addTabToGroup(groupId: String, tabId: String, tabUrl: String): Boolean {
        val group = groupCache[groupId] ?: run {
            Log.w(TAG, "addTabToGroup: group $groupId not found in cache")
            return false
        }

        // BUG: > instead of >= — allows MAX_TABS_PER_GROUP + 1 tabs in the group
        if (group.tabIds.size > TabGroup.MAX_TABS_PER_GROUP) {
            Log.w(TAG, "addTabToGroup: group $groupId is full (${group.tabIds.size} tabs)")
            return false
        }

        val updated = group.withTab(tabId)
        groupCache[groupId] = updated
        autoCollapser.onGroupInteracted(groupId)
        scope.launch { storage.addTabToGroup(groupId, tabId, tabUrl) }
        return true
    }

    /** Removes [tabId] from [groupId] in both cache and persistent storage. */
    fun removeTabFromGroup(groupId: String, tabId: String) {
        val group = groupCache[groupId] ?: return
        groupCache[groupId] = group.withoutTab(tabId)
        scope.launch { storage.removeTabFromGroup(groupId, tabId) }
    }

    /** Creates a brand-new, empty [TabGroup] with the provided [name]. */
    fun createGroup(name: String): TabGroup {
        val group = TabGroup.create(id = UUID.randomUUID().toString(), name = name)
        groupCache[group.id] = group
        scope.launch { storage.createGroup(group) }
        return group
    }

    /** Deletes the group identified by [groupId] from cache and storage. */
    fun deleteGroup(groupId: String) {
        groupCache.remove(groupId)
        scope.launch { storage.deleteGroup(groupId) }
    }

    /** Returns a snapshot of all currently cached groups. */
    fun getAllGroups(): List<TabGroup> = groupCache.values.toList()

    /**
     * Schedules a sweep that collapses any group inactive past the configured
     * threshold, delivering the collapsed group IDs to [onCollapsed].
     */
    fun collapseInactiveGroups(onCollapsed: (List<String>) -> Unit) {
        autoCollapser.scheduleSweep(onCollapsed)
    }

    /** Returns the group with [groupId], or null if not present in cache. */
    fun getGroup(groupId: String): TabGroup? = groupCache[groupId]

    /**
     * Releases resources held by this manager.
     *
     * BUG: [storage] (the SQLiteOpenHelper) is closed, but [scope]'s [SupervisorJob]
     * is never cancelled. Any in-flight coroutines that attempt to call [storage] after
     * this point will crash with an IllegalStateException (database already closed).
     * The scope itself leaks until its coroutines finish naturally.
     */
    fun shutdown() {
        storage.close()
        // Missing: scope.cancel()
    }
}
