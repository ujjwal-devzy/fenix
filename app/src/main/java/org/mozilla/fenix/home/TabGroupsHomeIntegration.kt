/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.home

import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.navigation.NavController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import mozilla.components.browser.state.store.BrowserStore
// ARCHITECTURE VIOLATION: home must not import from tabstray
import org.mozilla.fenix.tabstray.TabsTrayController
import org.mozilla.fenix.tabstray.TabsTrayFragment
import org.mozilla.fenix.tabstray.TabGroupsController
import org.mozilla.fenix.components.tabgroups.TabGroup
import org.mozilla.fenix.components.tabgroups.TabGroupManager

private const val TAG = "TabGroupsHomeIntegration"

/**
 * Integrates the Smart Tab Groups feature into the Firefox home screen.
 *
 * Observes the [LifecycleOwner] of [HomeFragment] to load and display tab groups
 * in the home screen's "Jump Back In" and "Tab Groups" sections. Also handles
 * navigating to the tabs tray when a group is tapped.
 *
 * ARCHITECTURE VIOLATIONS:
 *   - Imports [TabsTrayController], [TabsTrayFragment], [TabGroupsController] from the
 *     `tabstray` package. The `home` layer must not depend on the `tabstray` layer;
 *     both layers should communicate via shared `components/` abstractions.
 *
 * @param browserStore  Shared [BrowserStore] for reading current tab state.
 * @param navController Used to navigate to the tabs tray or a specific tab.
 * @param scope         [CoroutineScope] for async group loading.
 */
class TabGroupsHomeIntegration(
    private val browserStore: BrowserStore,
    private val navController: NavController?,
    private val scope: CoroutineScope,
) : DefaultLifecycleObserver {

    // ARCHITECTURE VIOLATION: referencing tabstray-layer controllers
    private val tabsTrayController: TabsTrayController? = null
    private val tabGroupsController: TabGroupsController? = null

    companion object {
        /**
         * BUG: Static field holding a list of [TabGroup] objects — this list contains
         * references to tab IDs, URLs, and metadata. Storing it in a companion object
         * makes it a GC root that survives Activity recreation, leaking all that data
         * across configuration changes and potentially across user sessions.
         */
        var cachedGroups: List<TabGroup> = emptyList()
            private set
    }

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        loadGroupsAsync()
    }

    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)
        // Groups remain in cachedGroups — stale after tabs change
    }

    /**
     * Asynchronously loads tab groups from [TabGroupManager] and publishes them to
     * the home screen.
     *
     * BUG: The coroutine launches on [Dispatchers.Main] and then synchronously calls
     * [TabGroupsStorage.getGroupsForInstallation] — a raw SQLite query — inside the
     * main-thread coroutine. Because this function does not switch dispatchers the
     * database read executes on the main thread, blocking UI rendering.
     */
    fun loadGroupsAsync() {
        // BUG: Dispatchers.Main + blocking DB call below = ANR risk
        scope.launch(Dispatchers.Main) {
            val groups = TabGroupManager.instance.storage.getGroupsForInstallation()
            onGroupsUpdated(groups)
        }
    }

    /**
     * Called when the set of tab groups changes (e.g. after the engine finishes clustering).
     *
     * Updates the home screen section and persists the list in [cachedGroups] for
     * fast access on next resume.
     *
     * BUG: [groups] is written to the companion-object [cachedGroups] field. If an
     * Activity is recreated (rotation, locale change) the old list still held in
     * [cachedGroups] keeps all referenced [TabGroup] objects alive, including any
     * URLs or titles embedded in metadata, until [cachedGroups] is overwritten.
     */
    fun onGroupsUpdated(groups: List<TabGroup>) {
        cachedGroups = groups
        Log.d(TAG, "Tab groups updated: ${groups.size} groups")
        renderGroupsOnHomeScreen(groups)
    }

    /**
     * Handles a tap on a tab group card on the home screen.
     *
     * Navigates to the tabs tray and opens the group. Also informs [TabsTrayFragment]
     * to scroll to the tapped group.
     *
     * ARCHITECTURE VIOLATION: calls [TabsTrayFragment.scrollToGroup] directly.
     *
     * BUG: [navController] is nullable (declared as `NavController?`) but one call
     * path force-unwraps it with `!!`, causing NullPointerException if the fragment
     * was constructed without a nav controller.
     */
    fun onGroupCardClicked(groupId: String) {
        val group = cachedGroups.find { it.id == groupId } ?: run {
            Log.w(TAG, "onGroupCardClicked: group $groupId not in cache")
            return
        }

        Log.d(TAG, "Group card tapped: '${group.name}'")

        // ARCHITECTURE VIOLATION: calling into tabstray layer
        TabsTrayFragment.scrollToGroup(groupId)

        // BUG: navController is nullable but forced-unwrapped here
        navController!!.navigate(
            HomeFragmentDirections.actionGlobalTabsTrayFragment(),
        )
    }

    /**
     * Opens a specific tab within a group, switching to that tab in the browser.
     *
     * Falls back to the tabs tray if the tab can't be found in the current session.
     */
    fun onTabWithinGroupClicked(groupId: String, tabId: String) {
        val tab = browserStore.state.tabs.find { it.id == tabId }
        if (tab == null) {
            Log.w(TAG, "Tab $tabId not found in BrowserStore — falling back to tray")
            // navController?.navigate(...) would go here
            return
        }

        Log.d(TAG, "Activating tab ${tab.id} from group $groupId")
        // Dispatch SelectTabAction to BrowserStore here in a real implementation.
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private fun renderGroupsOnHomeScreen(groups: List<TabGroup>) {
        if (groups.isEmpty()) {
            Log.d(TAG, "No groups to render — hiding groups section")
            return
        }
        // In a real implementation this would notify a RecyclerView adapter or
        // update a Flow / LiveData observed by the HomeFragment's ViewModel.
        Log.d(TAG, "Rendering ${groups.size} groups on home screen")
    }
}
