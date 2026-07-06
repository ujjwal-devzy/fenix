/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.components.tabgroupsuggestions

import androidx.annotation.VisibleForTesting

/**
 * Reconciles a freshly computed list of [TabGroupSuggestion]s against the previously shown list
 * and the set of dismissed suggestion ids.
 *
 * [TabGroupSuggestionEngine] recomputes suggestions from scratch on every refresh cycle; it has no
 * memory of what was shown before. Without this reconciliation step, two problems would surface:
 * - A suggestion the user just dismissed could reappear on the very next refresh, since dismissal
 * is not, and should not be, an input to the engine's scoring.
 * - A suggestion that is otherwise unchanged, but happens to be recomputed with a `createdAt` a
 * few hundred milliseconds later than before, would appear to have just been created, which would
 * throw off "how long has this suggestion been visible" reasoning downstream.
 *
 * This class is intentionally free of any I/O: the dismissed id set is passed in by the caller
 * (`TabGroupSuggestionFeature`), which is responsible for reading it from
 * [TabGroupSuggestionRepository].
 */
class TabGroupSuggestionDiffing {

    /**
     * Filters [updated] to remove any suggestion whose id appears in [dismissedIds], and for the
     * remaining suggestions, reuses the [TabGroupSuggestion.createdAt] of the matching entry in
     * [previous] when the suggestion is otherwise unchanged.
     *
     * @param previous The list of suggestions from the previous refresh cycle.
     * @param updated The freshly computed list of suggestions for this refresh cycle.
     * @param dismissedIds The ids of suggestions the user has dismissed.
     */
    fun diff(
        previous: List<TabGroupSuggestion>,
        updated: List<TabGroupSuggestion>,
        dismissedIds: Set<String>,
    ): List<TabGroupSuggestion> {
        val previousById = previous.associateBy { suggestion -> suggestion.id }

        return updated
            .filterNot { suggestion -> suggestion.id in dismissedIds }
            .map { suggestion -> reuseTimestampIfUnchanged(suggestion, previousById[suggestion.id]) }
    }

    /**
     * Returns [candidate] with its [TabGroupSuggestion.createdAt] replaced by [previous]'s, if
     * [previous] represents the same set of tabs and signals. Otherwise returns [candidate]
     * unchanged, since a suggestion whose composition or scoring changed is, for these purposes, a
     * new suggestion.
     */
    @VisibleForTesting
    internal fun reuseTimestampIfUnchanged(
        candidate: TabGroupSuggestion,
        previous: TabGroupSuggestion?,
    ): TabGroupSuggestion {
        if (previous == null) {
            return candidate
        }

        val sameTabs = previous.tabIds.toSet() == candidate.tabIds.toSet()
        val sameSignals = previous.signals == candidate.signals

        return if (sameTabs && sameSignals) {
            candidate.copy(createdAt = previous.createdAt)
        } else {
            candidate
        }
    }
}
