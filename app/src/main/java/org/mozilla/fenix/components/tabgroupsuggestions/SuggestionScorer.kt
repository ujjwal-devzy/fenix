/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.components.tabgroupsuggestions

/**
 * The relative weight given to each [SuggestionSignal] type when [SuggestionScorer] combines them
 * into a single ranking score.
 *
 * These are intentionally exposed as tunable, constructor-provided values rather than hard-coded
 * constants so that [TabGroupSuggestionEngine] can be experimented with, e.g. via Nimbus, without
 * changing the scoring algorithm itself.
 *
 * @property domainSimilarity Weight applied to [SuggestionSignal.DomainSimilarity] scores.
 * @property recency Weight applied to [SuggestionSignal.Recency] scores.
 * @property frequency Weight applied to [SuggestionSignal.Frequency] scores.
 * @property titleKeywordOverlap Weight applied to [SuggestionSignal.TitleKeywordOverlap] scores.
 */
data class SuggestionWeights(
    val domainSimilarity: Float = DEFAULT_DOMAIN_SIMILARITY_WEIGHT,
    val recency: Float = DEFAULT_RECENCY_WEIGHT,
    val frequency: Float = DEFAULT_FREQUENCY_WEIGHT,
    val titleKeywordOverlap: Float = DEFAULT_TITLE_KEYWORD_WEIGHT,
) {
    companion object {
        const val DEFAULT_DOMAIN_SIMILARITY_WEIGHT = 0.4f
        const val DEFAULT_RECENCY_WEIGHT = 0.2f
        const val DEFAULT_FREQUENCY_WEIGHT = 0.15f
        const val DEFAULT_TITLE_KEYWORD_WEIGHT = 0.25f
    }
}

/**
 * A [CandidateTabGroup] paired with the [SuggestionSignal]s that fired for it and the final,
 * combined score produced by [SuggestionScorer].
 */
data class ScoredCandidate(
    val candidate: CandidateTabGroup,
    val signals: List<SuggestionSignal>,
    val score: Float,
)

/**
 * Combines the independent [SuggestionSignal] outputs produced for a [CandidateTabGroup] into a
 * single weighted score in the `0f..1f` range.
 *
 * The combination is a weighted average rather than a simple sum so that a candidate for which
 * only one or two signals fired isn't unfairly penalized relative to one for which every signal
 * fired: each candidate is scored only against the weight of the signals that actually apply to
 * it.
 *
 * @property weights The [SuggestionWeights] used to combine signal scores.
 */
class SuggestionScorer(
    private val weights: SuggestionWeights = SuggestionWeights(),
) {

    /**
     * Combines [signals] into a single weighted score, or `0f` if [signals] is empty.
     */
    fun score(signals: List<SuggestionSignal>): Float {
        if (signals.isEmpty()) {
            return SuggestionSignal.MIN_SCORE
        }

        val weightedSum = signals.sumOf { signal -> weightFor(signal).toDouble() * signal.score.toDouble() }
        val totalWeight = signals.sumOf { signal -> weightFor(signal).toDouble() }

        if (totalWeight <= 0.0) {
            return SuggestionSignal.MIN_SCORE
        }

        return (weightedSum / totalWeight)
            .toFloat()
            .coerceIn(SuggestionSignal.MIN_SCORE, SuggestionSignal.MAX_SCORE)
    }

    /**
     * Convenience wrapper that pairs the result of [score] with the [candidate] and [signals] it
     * was computed from, ready to be handed to [SuggestionRanker].
     */
    fun scoreCandidate(candidate: CandidateTabGroup, signals: List<SuggestionSignal>): ScoredCandidate {
        return ScoredCandidate(
            candidate = candidate,
            signals = signals,
            score = score(signals),
        )
    }

    /**
     * Returns the configured [SuggestionWeights] entry that applies to [signal]'s type. Exposed
     * for testing so weighting bugs can be caught without having to reverse-engineer them from a
     * combined score.
     */
    internal fun weightFor(signal: SuggestionSignal): Float = when (signal) {
        is SuggestionSignal.DomainSimilarity -> weights.domainSimilarity
        is SuggestionSignal.Recency -> weights.recency
        is SuggestionSignal.Frequency -> weights.frequency
        is SuggestionSignal.TitleKeywordOverlap -> weights.titleKeywordOverlap
    }

    companion object {
        /**
         * The minimum combined score a candidate must reach for [SuggestionRanker] to consider it
         * worth surfacing to the user.
         */
        const val MEANINGFUL_SCORE_THRESHOLD = 0.35f
    }
}
