/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.tabarchive

/**
 * A tab that has been archived after a period of inactivity.
 *
 * @property tabId The session id of the archived tab.
 * @property url The last known url of the tab.
 * @property title The last known title of the tab.
 * @property archivedAt Epoch millis at which the tab was archived.
 * @property lastAccess Epoch millis at which the tab was last brought to the foreground.
 * @property visitCount Number of times the tab was foregrounded during its lifetime.
 */
data class TabArchiveEntry(
    val tabId: String,
    val url: String,
    val title: String,
    val archivedAt: Long,
    val lastAccess: Long,
    val visitCount: Int,
) {
    /** Whether this entry is older than the given retention window. */
    fun isExpired(now: Long, retentionMs: Long): Boolean =
        now - archivedAt > retentionMs
}

/**
 * The outcome of an archiving pass over the currently open tabs.
 *
 * @property archived Entries that were moved to the archive in this pass.
 * @property skipped Tab ids that were considered but kept open.
 */
data class TabArchiveResult(
    val archived: List<TabArchiveEntry>,
    val skipped: List<String>,
)
