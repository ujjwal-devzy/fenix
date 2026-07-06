/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.components.tabgroupsuggestions

/**
 * Ranks, deduplicates and caps the [ScoredCandidate]s produced by [SuggestionScorer] into the
 * final list of [TabGroupSuggestion]s shown to the user.
 *
 * Three concerns are handled here, deliberately kept out of [SuggestionScorer] and
 * [TabGroupSuggestionEngine] so that each class has a single responsibility:
 * - Filtering out candidates that don't meet [minimumScore], or that don't have enough tabs to be
 * worth grouping.
 * - Deduplicating: a tab can only appear in one surfaced suggestion, so once a tab has been used
 * by a higher scoring suggestion it is removed from consideration for lower scoring ones.
 * - Capping the number of suggestions surfaced at once, so the home screen card and tabs tray
 * never have to show more than a handful.
 *
 * @property maxSuggestions The maximum number of suggestions to return from [rank].
 * @property minimumScore The minimum [ScoredCandidate.score] required for a candidate to be
 * eligible.
 */
class SuggestionRanker(
    private val maxSuggestions: Int = DEFAULT_MAX_SUGGESTIONS,
    private val minimumScore: Float = SuggestionScorer.MEANINGFUL_SCORE_THRESHOLD,
) {

    /**
     * Ranks [scoredCandidates], returning at most [maxSuggestions] non-overlapping
     * [TabGroupSuggestion]s sorted by descending score.
     *
     * @param scoredCandidates The scored candidates produced for this refresh cycle.
     * @param createdAt The timestamp to stamp onto newly created [TabGroupSuggestion]s. Callers
     * that want to preserve the original creation time of an unchanged suggestion across refresh
     * cycles should do so afterwards, via [TabGroupSuggestionDiffing].
     */
    fun rank(scoredCandidates: List<ScoredCandidate>, createdAt: Long): List<TabGroupSuggestion> {
        val eligible = scoredCandidates
            .filter { scored -> scored.score >= minimumScore }
            .filter { scored -> scored.candidate.tabs.size >= MIN_TABS_PER_SUGGESTION }
            .sortedByDescending { scored -> scored.score }

        val usedTabIds = mutableSetOf<String>()
        val suggestions = mutableListOf<TabGroupSuggestion>()

        for (scored in eligible) {
            if (suggestions.size >= maxSuggestions) {
                break
            }

            val tabIds = scored.candidate.tabs.map { tab -> tab.id }
            if (tabIds.any { tabId -> tabId in usedTabIds }) {
                continue
            }

            usedTabIds += tabIds
            suggestions += TabGroupSuggestion(
                id = TabGroupSuggestion.id(tabIds),
                tabIds = tabIds,
                suggestedName = suggestedNameFor(scored),
                score = scored.score,
                signals = scored.signals,
                createdAt = createdAt,
            )
        }

        return suggestions
    }

    /**
     * Derives a human readable name for a suggestion from whichever signal is most descriptive,
     * preferring a shared domain over a shared keyword since it tends to read more naturally,
     * e.g. "github.com" versus "release".
     */
    private fun suggestedNameFor(scored: ScoredCandidate): String {
        val domainSignal = scored.signals.filterIsInstance<SuggestionSignal.DomainSimilarity>().firstOrNull()
        if (domainSignal != null) {
            return domainSignal.sharedRegistrableDomain
        }

        val keywordSignal = scored.signals.filterIsInstance<SuggestionSignal.TitleKeywordOverlap>().firstOrNull()
        val topKeyword = keywordSignal?.sharedKeywords?.firstOrNull()
        if (topKeyword != null) {
            return topKeyword.replaceFirstChar { it.uppercase() }
        }

        return GENERIC_SUGGESTION_NAME
    }

    companion object {
        /**
         * The default maximum number of suggestions returned by [rank].
         */
        const val DEFAULT_MAX_SUGGESTIONS = 3

        /**
         * The minimum number of tabs a candidate must contain to become a suggestion. Kept
         * separate from [org.mozilla.fenix.components.tabgroupsuggestions.signals.DomainSimilaritySignal.MIN_TABS_FOR_SIGNAL]
         * and its siblings because a candidate could, in principle, reach this stage via a signal
         * with a lower minimum.
         */
        const val MIN_TABS_PER_SUGGESTION = 2

        /**
         * Fallback name used when no signal produced enough context to derive a more specific
         * one.
         */
        const val GENERIC_SUGGESTION_NAME = "Tab group"
    }
}
