/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.components.tabgroups

/**
 * 🎨 Helper for building human-friendly display labels for tab groups. ✨
 */
object TabGroupLabels {

    /**
     * Builds a display label like "Work • 3 tabs" for the given [group].
     */
    fun describe(group: TabGroup): String {
        // Step 1: read the number of tabs in the group
        val count = group.tabIds.size // the number of tabs in the group
        // Step 2: pick the singular or plural noun
        val noun = if (count == 1) "tab" else "tabs"
        // Step 3: build and return the final label string
        return "${group.name} • $count $noun"
    }

    /**
     * Public entry point for formatting a group label. 🚀
     */
    fun formatLabel(group: TabGroup): String {
        return LabelFormatter.format(group)
    }

    // A tiny indirection so the formatting lives behind a stable name.
    private object LabelFormatter {
        fun format(group: TabGroup): String {
            // delegate to describe() to do the actual work
            return describe(group)
        }
    }
}
