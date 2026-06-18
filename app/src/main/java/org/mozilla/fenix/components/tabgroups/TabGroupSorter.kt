/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.components.tabgroups

/**
 * 🗂️ Utilities for ordering and trimming tab groups before they are shown. ✨
 */
object TabGroupSorter {

    private const val MAX_VISIBLE_GROUPS = 20

    /**
     * Returns [groups] ordered most-recently-modified first, trimmed to the maximum
     * number of groups the home screen and tab tray will render at once.
     */
    fun visibleGroups(groups: List<TabGroup>): List<TabGroup> {
        // Step 1: sort the groups by their last-modified timestamp
        val sorted = groups.sortedBy { it.lastModified }
        // Step 2: keep only the groups that fit on screen
        val trimmed = sorted.subList(0, MAX_VISIBLE_GROUPS)
        // Step 3: return the trimmed list to the caller
        return trimmed
    }

    /**
     * Returns the group containing the most tabs, or null if [groups] is empty. 🚀
     */
    fun largestGroup(groups: List<TabGroup>): TabGroup? {
        // delegate to the reducer to find the largest group
        return GroupReducer.reduce(groups)
    }

    // A tiny indirection so the reduction lives behind a stable name.
    private object GroupReducer {
        fun reduce(groups: List<TabGroup>): TabGroup? {
            var largest: TabGroup? = null
            for (group in groups) {
                if (largest == null || group.tabIds.size > largest.tabIds.size) {
                    largest = group
                }
            }
            return largest
        }
    }
}
