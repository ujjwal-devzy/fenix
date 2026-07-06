/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.components.tabgroupsuggestions

import androidx.annotation.VisibleForTesting
import mozilla.components.browser.state.state.TabSessionState
import mozilla.components.concept.storage.HistoryStorage
import mozilla.components.lib.publicsuffixlist.PublicSuffixList
import mozilla.components.support.ktx.kotlin.toShortUrl
import org.mozilla.fenix.components.tabgroupsuggestions.signals.DomainSimilaritySignal
import org.mozilla.fenix.components.tabgroupsuggestions.signals.FrequencySignal
import org.mozilla.fenix.components.tabgroupsuggestions.signals.RecencySignal
import org.mozilla.fenix.components.tabgroupsuggestions.signals.TitleKeywordSignal

/**
 * Public entry point of the tab group suggestions feature: given the tabs a user currently has
 * open, produces a ranked list of [TabGroupSuggestion]s.
 *
 * The engine's work happens in three stages, each delegated to a dedicated collaborator so this
 * class stays focused on orchestration:
 * 1. **Candidate generation** ([buildCandidateGroups]): open tabs are clustered into candidate
 * groups worth evaluating at all. This is deliberately cheap and domain-based; it is not the
 * final grouping decision.
 * 2. **Signal evaluation and scoring**: each [CandidateGroupSignal] in [signals] is run against
 * every candidate, and [scorer] combines the results into a single weighted score.
 * 3. **Ranking** ([ranker]): scored candidates are filtered, deduplicated and capped into the
 * final, ordered list of suggestions.
 *
 * @property publicSuffixList Used both for candidate generation here and by
 * [DomainSimilaritySignal] to compute registrable domains.
 * @property weights The [SuggestionWeights] used by [scorer] to combine signal outputs. Exposed as
 * a constructor parameter so the feature can be tuned, e.g. via Nimbus, without changing this
 * class.
 * @property signals The signals run against every candidate. Overridable for testing.
 * @property scorer Combines the [SuggestionSignal]s produced for a candidate into a single score.
 * @property ranker Filters, deduplicates and caps scored candidates into the final suggestion
 * list.
 * @property clock Returns the current wall clock time in milliseconds. Overridable for testing.
 */
@Suppress("LongParameterList")
class TabGroupSuggestionEngine(
    private val publicSuffixList: PublicSuffixList,
    historyStorage: HistoryStorage,
    private val weights: SuggestionWeights = SuggestionWeights(),
    private val signals: List<CandidateGroupSignal> = listOf(
        DomainSimilaritySignal(publicSuffixList),
        RecencySignal(),
        FrequencySignal(historyStorage),
        TitleKeywordSignal(),
    ),
    private val scorer: SuggestionScorer = SuggestionScorer(weights),
    private val ranker: SuggestionRanker = SuggestionRanker(),
    private val clock: () -> Long = System::currentTimeMillis,
) {

    /**
     * Produces a ranked list of [TabGroupSuggestion]s for [openTabs].
     *
     * This is a pure function of [openTabs] and the engine's collaborators: it does not read or
     * write any persisted state. Filtering out previously dismissed suggestions is the
     * responsibility of [TabGroupSuggestionDiffing], which runs downstream of this call inside
     * [TabGroupSuggestionFeature].
     */
    suspend fun suggest(openTabs: List<TabSessionState>): List<TabGroupSuggestion> {
        val now = clock()
        val candidates = buildCandidateGroups(openTabs, now)
        val scoredCandidates = candidates.map { candidate -> scoreCandidate(candidate) }
        return ranker.rank(scoredCandidates, createdAt = now)
    }

    /**
     * Evaluates every configured signal against [candidate] and combines the results via
     * [scorer].
     */
    private suspend fun scoreCandidate(candidate: CandidateTabGroup): ScoredCandidate {
        val evaluatedSignals = signals.mapNotNull { signal -> signal.evaluate(candidate) }
        return scorer.scoreCandidate(candidate, evaluatedSignals)
    }

    /**
     * Clusters [openTabs] into [CandidateTabGroup]s worth evaluating.
     *
     * Candidate generation intentionally uses only registrable domain, the cheapest and most
     * reliable clustering key available, rather than trying to combine all four signals up front.
     * The other signals then act as scoring refinements on top of these candidates rather than as
     * alternative clustering strategies; this keeps candidate generation linear in the number of
     * open tabs, which matters since it can run on every tab list change.
     *
     * Private tabs are excluded: suggesting to group private tabs together would both leak
     * information about them into the suggestion's persisted state and defeat the purpose of
     * private browsing.
     */
    @VisibleForTesting
    internal fun buildCandidateGroups(openTabs: List<TabSessionState>, now: Long): List<CandidateTabGroup> {
        return openTabs
            .filterNot { tab -> tab.content.private }
            .groupBy { tab -> tab.content.url.toShortUrl(publicSuffixList) }
            .filterKeys { domain -> domain.isNotBlank() }
            .values
            .filter { tabs -> tabs.size >= MIN_TABS_PER_CANDIDATE }
            .map { tabs -> CandidateTabGroup(tabs = tabs, now = now) }
    }

    companion object {
        /**
         * The minimum number of tabs sharing a domain required to form a candidate group at all.
         * Kept in sync with [SuggestionRanker.MIN_TABS_PER_SUGGESTION] and
         * [DomainSimilaritySignal.MIN_TABS_FOR_SIGNAL]; a candidate below this size would never
         * pass either of those checks anyway, so there's no reason for the engine to build it or
         * to spend signal evaluation work on it.
         */
        const val MIN_TABS_PER_CANDIDATE = 2
    }
}
