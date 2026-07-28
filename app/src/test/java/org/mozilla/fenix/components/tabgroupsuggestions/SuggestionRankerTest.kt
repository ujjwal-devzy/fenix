/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.components.tabgroupsuggestions

import mozilla.components.browser.state.state.createTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val CREATED_AT = 1_000L

class SuggestionRankerTest {

    @Test
    fun `GIVEN a candidate exactly at the minimum score WHEN rank is called THEN it is included`() {
        val ranker = SuggestionRanker(minimumScore = 0.5f)
        val candidate = scoredCandidate(score = 0.5f, tabIds = listOf("1", "2"))

        val result = ranker.rank(listOf(candidate), CREATED_AT)

        assertEquals(1, result.size)
    }

    @Test
    fun `GIVEN exactly as many eligible candidates as maxSuggestions WHEN rank is called THEN none are dropped`() {
        val ranker = SuggestionRanker(maxSuggestions = 2, minimumScore = 0f)
        val candidates = listOf(
            scoredCandidate(score = 0.9f, tabIds = listOf("1", "2")),
            scoredCandidate(score = 0.8f, tabIds = listOf("3", "4")),
        )

        val result = ranker.rank(candidates, CREATED_AT)

        assertEquals(2, result.size)
    }

    @Test
    fun `GIVEN the default constructor arguments WHEN a SuggestionRanker is built THEN it uses the documented defaults`() {
        val ranker = SuggestionRanker()
        val candidates = (1..(SuggestionRanker.DEFAULT_MAX_SUGGESTIONS + 2)).map { index ->
            scoredCandidate(
                score = SuggestionScorer.MEANINGFUL_SCORE_THRESHOLD,
                tabIds = listOf("${index}a", "${index}b"),
            )
        }

        val result = ranker.rank(candidates, CREATED_AT)

        assertEquals(SuggestionRanker.DEFAULT_MAX_SUGGESTIONS, result.size)
    }

    @Test
    fun `GIVEN a candidate below the minimum score WHEN rank is called THEN it is excluded`() {
        val ranker = SuggestionRanker(minimumScore = 0.5f)
        val candidate = scoredCandidate(score = 0.4f, tabIds = listOf("1", "2"))

        val result = ranker.rank(listOf(candidate), CREATED_AT)

        assertEquals(emptyList<TabGroupSuggestion>(), result)
    }

    @Test
    fun `GIVEN a candidate with too few tabs WHEN rank is called THEN it is excluded`() {
        val ranker = SuggestionRanker(minimumScore = 0f)
        val candidate = scoredCandidate(score = 0.9f, tabIds = listOf("1"))

        val result = ranker.rank(listOf(candidate), CREATED_AT)

        assertEquals(emptyList<TabGroupSuggestion>(), result)
    }

    @Test
    fun `GIVEN multiple eligible candidates WHEN rank is called THEN they are sorted by descending score`() {
        val ranker = SuggestionRanker(minimumScore = 0f)
        val low = scoredCandidate(score = 0.3f, tabIds = listOf("1", "2"))
        val high = scoredCandidate(score = 0.9f, tabIds = listOf("3", "4"))
        val medium = scoredCandidate(score = 0.6f, tabIds = listOf("5", "6"))

        val result = ranker.rank(listOf(low, high, medium), CREATED_AT)

        assertEquals(listOf(0.9f, 0.6f, 0.3f), result.map { it.score })
    }

    @Test
    fun `GIVEN more eligible candidates than maxSuggestions WHEN rank is called THEN the list is capped`() {
        val ranker = SuggestionRanker(maxSuggestions = 2, minimumScore = 0f)
        val candidates = listOf(
            scoredCandidate(score = 0.9f, tabIds = listOf("1", "2")),
            scoredCandidate(score = 0.8f, tabIds = listOf("3", "4")),
            scoredCandidate(score = 0.7f, tabIds = listOf("5", "6")),
        )

        val result = ranker.rank(candidates, CREATED_AT)

        assertEquals(2, result.size)
        assertEquals(listOf(0.9f, 0.8f), result.map { it.score })
    }

    @Test
    fun `GIVEN a low scoring candidate whose tabs overlap with a rejected higher scoring one WHEN rank is called THEN it can still be kept`() {
        val ranker = SuggestionRanker(minimumScore = 0.6f)
        // The first candidate does not clear the minimum score, so it should never mark tabs "1"
        // and "2" as used, leaving the second candidate free to claim them.
        val belowThreshold = scoredCandidate(score = 0.4f, tabIds = listOf("1", "2"))
        val eligible = scoredCandidate(score = 0.7f, tabIds = listOf("2", "3"))

        val result = ranker.rank(listOf(belowThreshold, eligible), CREATED_AT)

        assertEquals(1, result.size)
        assertEquals(listOf("2", "3"), result.single().tabIds)
    }

    @Test
    fun `GIVEN three mutually overlapping candidates WHEN rank is called THEN only the highest scoring one survives`() {
        val ranker = SuggestionRanker(minimumScore = 0f)
        val best = scoredCandidate(score = 0.9f, tabIds = listOf("1", "2"))
        val second = scoredCandidate(score = 0.8f, tabIds = listOf("2", "3"))
        val third = scoredCandidate(score = 0.7f, tabIds = listOf("1", "3"))

        val result = ranker.rank(listOf(best, second, third), CREATED_AT)

        assertEquals(1, result.size)
        assertEquals(listOf("1", "2"), result.single().tabIds)
    }

    @Test
    fun `GIVEN two candidates share a tab WHEN rank is called THEN only the higher scoring one is kept`() {
        val ranker = SuggestionRanker(minimumScore = 0f)
        val higherScoring = scoredCandidate(score = 0.9f, tabIds = listOf("1", "2"))
        val overlapping = scoredCandidate(score = 0.5f, tabIds = listOf("2", "3"))

        val result = ranker.rank(listOf(higherScoring, overlapping), CREATED_AT)

        assertEquals(1, result.size)
        assertEquals(listOf("1", "2"), result.single().tabIds)
    }

    @Test
    fun `GIVEN non-overlapping candidates WHEN rank is called THEN all of them are kept up to the cap`() {
        val ranker = SuggestionRanker(minimumScore = 0f)
        val first = scoredCandidate(score = 0.9f, tabIds = listOf("1", "2"))
        val second = scoredCandidate(score = 0.8f, tabIds = listOf("3", "4"))

        val result = ranker.rank(listOf(first, second), CREATED_AT)

        assertEquals(2, result.size)
    }

    @Test
    fun `GIVEN an eligible candidate WHEN rank is called THEN the suggestion id is derived from its sorted tab ids`() {
        val ranker = SuggestionRanker(minimumScore = 0f)
        val candidate = scoredCandidate(score = 0.9f, tabIds = listOf("2", "1"))

        val result = ranker.rank(listOf(candidate), CREATED_AT)

        assertEquals(TabGroupSuggestion.id(listOf("1", "2")), result.single().id)
    }

    @Test
    fun `GIVEN an eligible candidate WHEN rank is called THEN createdAt is stamped onto the suggestion`() {
        val ranker = SuggestionRanker(minimumScore = 0f)
        val candidate = scoredCandidate(score = 0.9f, tabIds = listOf("1", "2"))

        val result = ranker.rank(listOf(candidate), CREATED_AT)

        assertEquals(CREATED_AT, result.single().createdAt)
    }

    @Test
    fun `GIVEN a candidate with a domain similarity signal WHEN rank is called THEN the shared domain becomes the suggested name`() {
        val ranker = SuggestionRanker(minimumScore = 0f)
        val candidate = scoredCandidate(
            score = 0.9f,
            tabIds = listOf("1", "2"),
            signals = listOf(
                SuggestionSignal.DomainSimilarity(
                    score = 0.9f,
                    rationale = "shared domain",
                    sharedRegistrableDomain = "github.com",
                ),
                SuggestionSignal.TitleKeywordOverlap(
                    score = 0.3f,
                    rationale = "shared keyword",
                    sharedKeywords = listOf("release"),
                ),
            ),
        )

        val result = ranker.rank(listOf(candidate), CREATED_AT)

        assertEquals("github.com", result.single().suggestedName)
    }

    @Test
    fun `GIVEN a candidate with only a title keyword signal WHEN rank is called THEN the top keyword becomes the suggested name`() {
        val ranker = SuggestionRanker(minimumScore = 0f)
        val candidate = scoredCandidate(
            score = 0.9f,
            tabIds = listOf("1", "2"),
            signals = listOf(
                SuggestionSignal.TitleKeywordOverlap(
                    score = 0.3f,
                    rationale = "shared keyword",
                    sharedKeywords = listOf("android", "release"),
                ),
            ),
        )

        val result = ranker.rank(listOf(candidate), CREATED_AT)

        assertEquals("Android", result.single().suggestedName)
    }

    @Test
    fun `GIVEN a candidate with no descriptive signal WHEN rank is called THEN a generic name is used`() {
        val ranker = SuggestionRanker(minimumScore = 0f)
        val candidate = scoredCandidate(
            score = 0.9f,
            tabIds = listOf("1", "2"),
            signals = listOf(
                SuggestionSignal.Recency(score = 0.9f, rationale = "recent", averageAgeMillis = 1_000L),
            ),
        )

        val result = ranker.rank(listOf(candidate), CREATED_AT)

        assertEquals(SuggestionRanker.GENERIC_SUGGESTION_NAME, result.single().suggestedName)
    }

    @Test
    fun `GIVEN a candidate with a single-word keyword WHEN rank is called THEN the suggested name is capitalized`() {
        val ranker = SuggestionRanker(minimumScore = 0f)
        val candidate = scoredCandidate(
            score = 0.9f,
            tabIds = listOf("1", "2"),
            signals = listOf(
                SuggestionSignal.TitleKeywordOverlap(
                    score = 0.3f,
                    rationale = "shared keyword",
                    sharedKeywords = listOf("release"),
                ),
            ),
        )

        val result = ranker.rank(listOf(candidate), CREATED_AT)

        assertEquals("Release", result.single().suggestedName)
    }

    @Test
    fun `GIVEN no candidates at all WHEN rank is called THEN an empty list is returned`() {
        val ranker = SuggestionRanker()

        val result = ranker.rank(emptyList(), CREATED_AT)

        assertEquals(emptyList<TabGroupSuggestion>(), result)
    }

    @Test
    fun `GIVEN eligible candidates WHEN rank is called THEN the returned suggestions carry over their contributing signals`() {
        val ranker = SuggestionRanker(minimumScore = 0f)
        val signals = listOf(
            SuggestionSignal.DomainSimilarity(score = 0.9f, rationale = "r", sharedRegistrableDomain = "example.com"),
        )
        val candidate = scoredCandidate(score = 0.9f, tabIds = listOf("1", "2"), signals = signals)

        val result = ranker.rank(listOf(candidate), CREATED_AT)

        assertTrue(result.single().signals === signals)
    }

    private fun scoredCandidate(
        score: Float,
        tabIds: List<String>,
        signals: List<SuggestionSignal> = emptyList(),
    ): ScoredCandidate {
        val tabs = tabIds.map { id -> createTab(url = "https://example.com/$id", id = id) }
        return ScoredCandidate(
            candidate = CandidateTabGroup(tabs = tabs),
            signals = signals,
            score = score,
        )
    }
}
