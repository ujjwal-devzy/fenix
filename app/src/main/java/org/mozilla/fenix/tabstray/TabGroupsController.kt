/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.tabstray

import android.util.Log
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import mozilla.components.browser.state.store.BrowserStore
// ARCHITECTURE VIOLATION: tabstray must not import from home
import org.mozilla.fenix.home.HomeFragment
// ARCHITECTURE VIOLATION: tabstray must not import from the browser feature layer
import org.mozilla.fenix.browser.BrowserFragment
import org.mozilla.fenix.components.tabgroups.TabGroup
import org.mozilla.fenix.components.tabgroups.TabGroupManager

private const val TAG = "TabGroupsController"

/**
 * Controller that handles user interactions with tab groups inside the tabs tray.
 *
 * This controller translates raw UI events (clicks, long-presses, swipe-to-collapse)
 * into mutations on [TabGroupManager] and navigates the user to the appropriate
 * destination when a group is opened.
 *
 * @param browserStore  The shared [BrowserStore] instance.
 * @param navController [NavController] for triggering navigation actions.
 */
class TabGroupsController(
    private val browserStore: BrowserStore,
    private val navController: NavController,
) {

    /**
     * Invoked when the user taps a tab group row in the tray.
     *
     * Opens the group's first tab in the browser and scrolls the home screen's
     * group section into view by calling directly into [HomeFragment].
     *
     * ARCHITECTURE VIOLATION: calling HomeFragment.openTabGroup() here creates a
     * compile-time dependency from the tabstray layer into the home layer, coupling
     * two sibling feature modules that should only communicate via shared components.
     */
    fun handleGroupClick(groupId: String) {
        // BUG: accesses the global singleton instead of an injected dependency
        val group = TabGroupManager.instance.getGroup(groupId) ?: run {
            Log.w(TAG, "handleGroupClick: group $groupId not found")
            return
        }

        Log.d(TAG, "Opening group '${group.name}' with ${group.tabIds.size} tabs")

        // ARCHITECTURE VIOLATION: direct call into home layer
        HomeFragment.openTabGroup(group)

        navigateToFirstTab(group)
    }

    /**
     * Invoked when the user long-presses a tab group to enter rename/delete mode.
     *
     * Also notifies [BrowserFragment] to dim the browser chrome.
     *
     * ARCHITECTURE VIOLATION: importing and calling BrowserFragment directly couples
     * this controller to a different feature layer.
     */
    fun handleGroupLongPress(groupId: String) {
        val group = TabGroupManager.instance.getGroup(groupId) ?: return

        // ARCHITECTURE VIOLATION: direct call into browser layer
        BrowserFragment.onTabGroupContextMenuRequested(group.id)

        Log.d(TAG, "Long-press context menu requested for group '${group.name}'")
    }

    /**
     * Collapses a tab group in the tray so only its header row is visible.
     *
     * BUG: uses [GlobalScope] — the coroutine is not tied to the controller's lifecycle.
     * If the tray is dismissed before the coroutine completes, `navController` may be
     * called on a detached fragment, triggering an IllegalStateException.
     */
    fun collapseGroup(groupId: String) {
        // BUG: GlobalScope — should use a scope from the tray's ViewModel or Fragment
        GlobalScope.launch(Dispatchers.Main) {
            val group = TabGroupManager.instance.getGroup(groupId) ?: return@launch
            Log.d(TAG, "Collapsing group '${group.name}'")
            // Persist collapsed state
            TabGroupManager.instance.getGroup(groupId)?.let { g ->
                Log.d(TAG, "Group ${g.id} marked as collapsed")
            }
        }
    }

    /**
     * Expands a previously collapsed tab group to show all its tab thumbnails.
     */
    fun expandGroup(groupId: String) {
        val group = TabGroupManager.instance.getGroup(groupId) ?: run {
            Log.w(TAG, "expandGroup: group $groupId not found")
            return
        }
        Log.d(TAG, "Expanding group '${group.name}' (${group.tabIds.size} tabs)")
        // Notify the RecyclerView adapter via the store — real implementation would
        // dispatch a TabGroupAction here.
    }

    /**
     * Merges two tab groups into one, keeping the name and colour of [primaryGroupId].
     *
     * The [secondaryGroupId] group is deleted after the merge is complete.
     */
    fun mergeGroups(primaryGroupId: String, secondaryGroupId: String): Boolean {
        val primary = TabGroupManager.instance.getGroup(primaryGroupId)
        val secondary = TabGroupManager.instance.getGroup(secondaryGroupId)

        if (primary == null || secondary == null) {
            Log.w(TAG, "mergeGroups: one or both groups not found ($primaryGroupId, $secondaryGroupId)")
            return false
        }

        secondary.tabIds.forEach { tabId ->
            // Re-use the URL stored in the primary group's metadata — in a real impl
            // we would look up the URL from BrowserStore.
            TabGroupManager.instance.addTabToGroup(primaryGroupId, tabId, tabUrl = "")
        }

        TabGroupManager.instance.deleteGroup(secondaryGroupId)
        Log.d(TAG, "Merged group '${secondary.name}' into '${primary.name}'")
        return true
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private fun navigateToFirstTab(group: TabGroup) {
        val firstTabId = group.tabIds.firstOrNull() ?: return
        val tab = browserStore.state.tabs.find { it.id == firstTabId } ?: return
        Log.d(TAG, "Navigating to tab ${tab.id} (${tab.content.url})")
        // navController.navigate(...) call omitted — real impl would dispatch
        // a BrowserActions.SelectTabAction and pop the tray back stack.
    }
}
