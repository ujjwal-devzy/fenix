/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.components.tabgroupsuggestions

import mozilla.components.browser.state.state.TabSessionState

/**
 * The normalized output of a single, independent scoring signal evaluated by a
 * [CandidateGroupSignal] against a [CandidateTabGroup].
 *
 * Each signal type contributes a [score] in the `0f..1f` range plus enough context, via
 * [rationale] and its own fields, for [SuggestionScorer] to combine it with the other signals and
 * for the UI layer to explain to the user why a suggestion was made.
 */
sealed class SuggestionSignal {

    /**
     * How strongly this signal supports grouping the candidate's tabs together, from `0f` (no
     * support) to `1f` (very strong support).
     */
    abstract val score: Float

    /**
     * A short, human readable explanation of why this signal produced [score]. Not shown directly
     * to users, but useful for telemetry debugging and for building suggestion subtitles.
     */
    abstract val rationale: String

    /**
     * Fired when a candidate group's tabs mostly share the same registrable domain.
     *
     * @property sharedRegistrableDomain The domain that most of the candidate's tabs have in
     * common, as computed by [org.mozilla.fenix.components.tabgroupsuggestions.signals.DomainSimilaritySignal].
     */
    data class DomainSimilarity(
        override val score: Float,
        override val rationale: String,
        val sharedRegistrableDomain: String,
    ) : SuggestionSignal()

    /**
     * Fired when a candidate group's tabs were all accessed within a short window of one another.
     *
     * @property averageAgeMillis The average time, in milliseconds, since the candidate's tabs
     * were last accessed.
     */
    data class Recency(
        override val score: Float,
        override val rationale: String,
        val averageAgeMillis: Long,
    ) : SuggestionSignal()

    /**
     * Fired when a candidate group's tabs correspond to pages the user visits often.
     *
     * @property averageVisitCount The average number of recorded history visits across the
     * candidate's tabs over the signal's lookback window.
     */
    data class Frequency(
        override val score: Float,
        override val rationale: String,
        val averageVisitCount: Int,
    ) : SuggestionSignal()

    /**
     * Fired when a candidate group's tabs share meaningful keywords in their page titles.
     *
     * @property sharedKeywords The keywords found in every tab title in the candidate, sorted
     * alphabetically.
     */
    data class TitleKeywordOverlap(
        override val score: Float,
        override val rationale: String,
        val sharedKeywords: List<String>,
    ) : SuggestionSignal()

    companion object {
        /**
         * The minimum possible value of [score]. Signals should never emit a value lower than
         * this; they should instead return `null` from [CandidateGroupSignal.evaluate].
         */
        const val MIN_SCORE = 0f

        /**
         * The maximum possible value of [score].
         */
        const val MAX_SCORE = 1f
    }
}

/**
 * A candidate grouping of currently open tabs being evaluated by the suggestion signals, prior to
 * scoring and ranking.
 *
 * @property tabs The tabs under consideration for this candidate group.
 * @property now Wall clock time, in milliseconds, that the candidate is being evaluated at.
 * Signals that reason about elapsed time measure against this instead of calling
 * `System.currentTimeMillis` directly, so that they stay deterministic under test.
 */
data class CandidateTabGroup(
    val tabs: List<TabSessionState>,
    val now: Long = System.currentTimeMillis(),
)

/**
 * Contract implemented by each of the independent signals (domain similarity, recency, frequency,
 * title keyword overlap, ...) that the [TabGroupSuggestionEngine] runs against a
 * [CandidateTabGroup].
 *
 * Implementations should be side-effect free and safe to run concurrently across multiple
 * candidates; the engine may evaluate several candidates in the same refresh cycle.
 */
fun interface CandidateGroupSignal {

    /**
     * Evaluates [candidate], returning a [SuggestionSignal] describing how strongly this signal
     * supports grouping the tabs together, or `null` if the signal does not apply - for example
     * because the candidate doesn't have enough tabs, or none of its tabs meet the signal's
     * minimum threshold.
     */
    suspend fun evaluate(candidate: CandidateTabGroup): SuggestionSignal?
}
