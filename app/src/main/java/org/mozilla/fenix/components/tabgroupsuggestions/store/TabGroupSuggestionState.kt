/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.components.tabgroupsuggestions.store

import mozilla.components.lib.state.State
import org.mozilla.fenix.components.tabgroupsuggestions.TabGroupSuggestion

/**
 * The state held by [TabGroupSuggestionStore].
 *
 * @property suggestions The current, ranked list of [TabGroupSuggestion]s that have not been
 * dismissed. Consumed directly by the home screen card and tabs tray binding; both simply render
 * `suggestions.firstOrNull()`.
 * @property dismissedSuggestionIds A local cache of dismissed suggestion ids, mirroring what is
 * persisted by `org.mozilla.fenix.components.tabgroupsuggestions.TabGroupSuggestionRepository`.
 * Kept here too so that a dismissal is reflected in the UI immediately, without waiting on a
 * round trip through disk.
 * @property isEnabled Whether the user has the feature turned on. When `false`, [suggestions] is
 * always kept empty.
 * @property status The current [TabGroupSuggestionStatus] of the suggestion pipeline.
 */
data class TabGroupSuggestionState(
    val suggestions: List<TabGroupSuggestion> = emptyList(),
    val dismissedSuggestionIds: Set<String> = emptySet(),
    val isEnabled: Boolean = true,
    val status: TabGroupSuggestionStatus = TabGroupSuggestionStatus.Idle,
) : State

/**
 * The status of the suggestion refresh pipeline, used by the UI layer to decide whether to show a
 * loading state.
 */
enum class TabGroupSuggestionStatus {
    /**
     * No refresh is currently in progress.
     */
    Idle,

    /**
     * A refresh triggered by a change in the user's open tabs is currently in progress.
     */
    Refreshing,
}
