/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.components.tabgroupsuggestions

import org.junit.Assert.assertEquals
import org.junit.Test

private const val FLOAT_DELTA = 0.001f

class SuggestionScorerTest {

    private val candidate = CandidateTabGroup(tabs = emptyList())

    @Test
    fun `GIVEN no signals WHEN score is called THEN the minimum score is returned`() {
        val scorer = SuggestionScorer()

        assertEquals(SuggestionSignal.MIN_SCORE, scorer.score(emptyList()), FLOAT_DELTA)
    }

    @Test
    fun `GIVEN a score of exactly the minimum WHEN score is called THEN the minimum score is returned`() {
        val scorer = SuggestionScorer()

        assertEquals(SuggestionSignal.MIN_SCORE, scorer.score(listOf(domainSignal(score = 0f))), FLOAT_DELTA)
    }

    @Test
    fun `GIVEN a score of exactly the maximum WHEN score is called THEN the maximum score is returned`() {
        val scorer = SuggestionScorer()

        assertEquals(SuggestionSignal.MAX_SCORE, scorer.score(listOf(domainSignal(score = 1f))), FLOAT_DELTA)
    }

    @Test
    fun `GIVEN a single signal WHEN score is called THEN the combined score equals that signal's own score`() {
        val scorer = SuggestionScorer()
        val signal = domainSignal(score = 0.8f)

        assertEquals(0.8f, scorer.score(listOf(signal)), FLOAT_DELTA)
    }

    @Test
    fun `GIVEN only one signal fired WHEN score is called THEN the candidate is not penalized for missing signals`() {
        val scorer = SuggestionScorer()

        // Domain similarity's weight (0.4) is less than half of the total possible weight, but a
        // candidate for which it's the only signal that fired should still score close to what
        // the signal itself reported, not diluted towards zero as if the missing signals counted
        // against it.
        val signal = domainSignal(score = 0.9f)

        assertEquals(0.9f, scorer.score(listOf(signal)), FLOAT_DELTA)
    }

    @Test
    fun `GIVEN multiple signals WHEN score is called THEN they are combined as a weighted average`() {
        val scorer = SuggestionScorer(
            weights = SuggestionWeights(domainSimilarity = 0.4f, recency = 0.2f),
        )

        val signals = listOf(
            domainSignal(score = 1.0f),
            recencySignal(score = 0.5f),
        )

        // (0.4 * 1.0 + 0.2 * 0.5) / (0.4 + 0.2) = 0.5 / 0.6
        assertEquals(0.5f / 0.6f, scorer.score(signals), FLOAT_DELTA)
    }

    @Test
    fun `GIVEN custom weights WHEN score is called THEN the relative contribution of each signal changes`() {
        val domainHeavyScorer = SuggestionScorer(
            weights = SuggestionWeights(domainSimilarity = 0.9f, titleKeywordOverlap = 0.1f),
        )
        val keywordHeavyScorer = SuggestionScorer(
            weights = SuggestionWeights(domainSimilarity = 0.1f, titleKeywordOverlap = 0.9f),
        )

        val signals = listOf(
            domainSignal(score = 1.0f),
            titleKeywordSignal(score = 0.2f),
        )

        val domainHeavyScore = domainHeavyScorer.score(signals)
        val keywordHeavyScore = keywordHeavyScorer.score(signals)

        assertEquals(true, domainHeavyScore > keywordHeavyScore)
    }

    @Test
    fun `GIVEN all relevant weights are zero WHEN score is called THEN the minimum score is returned`() {
        val scorer = SuggestionScorer(
            weights = SuggestionWeights(
                domainSimilarity = 0f,
                recency = 0f,
                frequency = 0f,
                titleKeywordOverlap = 0f,
            ),
        )

        assertEquals(SuggestionSignal.MIN_SCORE, scorer.score(listOf(domainSignal(score = 1f))), FLOAT_DELTA)
    }

    @Test
    fun `GIVEN a signal reporting an out-of-range score WHEN score is called THEN the result is coerced into 0f to 1f`() {
        val scorer = SuggestionScorer()

        // Signals are expected to only ever produce scores within 0f..1f, but the combination
        // step defensively coerces its result anyway, since it is cheap insurance against a
        // future signal implementation bug producing an out-of-range value.
        val signal = domainSignal(score = 5f)

        assertEquals(SuggestionSignal.MAX_SCORE, scorer.score(listOf(signal)), FLOAT_DELTA)
    }

    @Test
    fun `GIVEN a candidate and signals WHEN scoreCandidate is called THEN the result pairs them with the computed score`() {
        val scorer = SuggestionScorer()
        val signals = listOf(domainSignal(score = 0.6f))

        val result = scorer.scoreCandidate(candidate, signals)

        assertEquals(candidate, result.candidate)
        assertEquals(signals, result.signals)
        assertEquals(0.6f, result.score, FLOAT_DELTA)
    }

    @Test
    fun `GIVEN all four signal types fire WHEN score is called THEN every weight contributes to the result`() {
        val scorer = SuggestionScorer(
            weights = SuggestionWeights(
                domainSimilarity = 0.4f,
                recency = 0.2f,
                frequency = 0.15f,
                titleKeywordOverlap = 0.25f,
            ),
        )
        val signals = listOf(
            domainSignal(score = 1.0f),
            recencySignal(score = 1.0f),
            frequencySignal(score = 1.0f),
            titleKeywordSignal(score = 1.0f),
        )

        // Every signal reports the maximum score, so regardless of the individual weights the
        // weighted average must also be the maximum.
        assertEquals(SuggestionSignal.MAX_SCORE, scorer.score(signals), FLOAT_DELTA)
    }

    @Test
    fun `GIVEN the default weights WHEN a SuggestionScorer is constructed without arguments THEN they match the documented defaults`() {
        val scorer = SuggestionScorer()

        assertEquals(SuggestionWeights.DEFAULT_DOMAIN_SIMILARITY_WEIGHT, scorer.weightFor(domainSignal(score = 0f)), FLOAT_DELTA)
        assertEquals(SuggestionWeights.DEFAULT_RECENCY_WEIGHT, scorer.weightFor(recencySignal(score = 0f)), FLOAT_DELTA)
        assertEquals(SuggestionWeights.DEFAULT_FREQUENCY_WEIGHT, scorer.weightFor(frequencySignal(score = 0f)), FLOAT_DELTA)
        assertEquals(
            SuggestionWeights.DEFAULT_TITLE_KEYWORD_WEIGHT,
            scorer.weightFor(titleKeywordSignal(score = 0f)),
            FLOAT_DELTA,
        )
    }

    @Test
    fun `GIVEN the default MEANINGFUL_SCORE_THRESHOLD WHEN compared to a borderline score THEN it excludes scores below it`() {
        val scorer = SuggestionScorer()
        val justBelowThreshold = SuggestionScorer.MEANINGFUL_SCORE_THRESHOLD - 0.01f

        val signal = domainSignal(score = justBelowThreshold)

        assertEquals(justBelowThreshold, scorer.score(listOf(signal)), FLOAT_DELTA)
        assertEquals(true, scorer.score(listOf(signal)) < SuggestionScorer.MEANINGFUL_SCORE_THRESHOLD)
    }

    @Test
    fun `GIVEN each signal type WHEN weightFor is called THEN the configured weight for that type is returned`() {
        val weights = SuggestionWeights(
            domainSimilarity = 0.11f,
            recency = 0.22f,
            frequency = 0.33f,
            titleKeywordOverlap = 0.44f,
        )
        val scorer = SuggestionScorer(weights)

        assertEquals(0.11f, scorer.weightFor(domainSignal(score = 0f)), FLOAT_DELTA)
        assertEquals(0.22f, scorer.weightFor(recencySignal(score = 0f)), FLOAT_DELTA)
        assertEquals(0.33f, scorer.weightFor(frequencySignal(score = 0f)), FLOAT_DELTA)
        assertEquals(0.44f, scorer.weightFor(titleKeywordSignal(score = 0f)), FLOAT_DELTA)
    }

    private fun domainSignal(score: Float) = SuggestionSignal.DomainSimilarity(
        score = score,
        rationale = "shared domain",
        sharedRegistrableDomain = "example.com",
    )

    private fun recencySignal(score: Float) = SuggestionSignal.Recency(
        score = score,
        rationale = "recently accessed",
        averageAgeMillis = 1_000L,
    )

    private fun frequencySignal(score: Float) = SuggestionSignal.Frequency(
        score = score,
        rationale = "frequently visited",
        averageVisitCount = 5,
    )

    private fun titleKeywordSignal(score: Float) = SuggestionSignal.TitleKeywordOverlap(
        score = score,
        rationale = "shared keyword",
        sharedKeywords = listOf("release"),
    )
}
