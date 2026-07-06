/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.components.tabgroupsuggestions

/**
 * A candidate grouping of currently open tabs that the [TabGroupSuggestionEngine] believes the
 * user may want to organize together, along with the score and signals that produced it.
 *
 * Instances of this class are immutable snapshots: they are recomputed on every refresh cycle of
 * the [TabGroupSuggestionEngine] rather than mutated in place. [TabGroupSuggestionDiffing] is
 * responsible for reconciling successive snapshots so that the UI layer doesn't flicker or
 * re-surface a suggestion the user has already dismissed.
 *
 * @property id A stable identifier for this suggestion, derived from the sorted set of [tabIds]
 * via [TabGroupSuggestion.id]. Because it depends only on tab membership, the same group of tabs
 * always produces the same id across refresh cycles, which is what allows dismissals to persist.
 * @property tabIds The ids of the tabs that make up this suggestion, matching the identifiers used
 * by `mozilla.components`' `BrowserStore`.
 * @property suggestedName A human readable name proposed for the resulting tab group, e.g. derived
 * from a shared domain or a shared keyword across tab titles.
 * @property score The combined, weighted score produced by the [SuggestionScorer]. Higher values
 * indicate a stronger recommendation. Always within `0f..1f`.
 * @property signals The individual [SuggestionSignal] outputs that contributed to [score], kept
 * around so the UI and telemetry layers can explain why a suggestion was made.
 * @property createdAt Wall clock time, in milliseconds, when this suggestion was first generated.
 * [TabGroupSuggestionDiffing] preserves this value across refreshes for unchanged suggestions so
 * that "how long has this been shown" telemetry stays accurate.
 */
data class TabGroupSuggestion(
    val id: String,
    val tabIds: List<String>,
    val suggestedName: String,
    val score: Float,
    val signals: List<SuggestionSignal>,
    val createdAt: Long,
) {

    /**
     * The number of tabs this suggestion proposes grouping together.
     */
    val tabCount: Int
        get() = tabIds.size

    companion object {

        /**
         * Derives a stable suggestion id from a collection of tab ids. The ids are sorted before
         * joining so that the same set of tabs always produces the same suggestion id regardless
         * of the order they were discovered in during a given refresh cycle.
         */
        fun id(tabIds: Collection<String>): String = tabIds.sorted().joinToString(separator = ID_SEPARATOR)

        private const val ID_SEPARATOR = "-"
    }
}
