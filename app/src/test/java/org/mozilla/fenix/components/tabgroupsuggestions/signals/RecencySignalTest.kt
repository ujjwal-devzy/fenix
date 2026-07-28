/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.components.tabgroupsuggestions.signals

import kotlinx.coroutines.test.runTest
import mozilla.components.browser.state.state.createTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mozilla.fenix.components.tabgroupsuggestions.CandidateTabGroup
import org.mozilla.fenix.components.tabgroupsuggestions.SuggestionSignal
import java.util.concurrent.TimeUnit

private const val FLOAT_DELTA = 0.001f
private const val NOW = 1_000_000_000L

class RecencySignalTest {

    @Test
    fun `GIVEN no tabs WHEN evaluate is called THEN null is returned`() {
        val signal = RecencySignal()
        val candidate = CandidateTabGroup(tabs = emptyList(), now = NOW)

        val result = runTest { signal.evaluate(candidate) }

        assertNull(result)
    }

    @Test
    fun `GIVEN the average age is exactly the recent window WHEN evaluate is called THEN a signal is still returned with the minimum score`() {
        val window = TimeUnit.HOURS.toMillis(1)
        val signal = RecencySignal(recentWindowMillis = window)
        val candidate = candidateWithAges(window)

        val result = runTest { signal.evaluate(candidate) } as SuggestionSignal.Recency

        assertEquals(SuggestionSignal.MIN_SCORE, result.score, FLOAT_DELTA)
    }

    @Test
    fun `GIVEN the average age is just past the recent window WHEN evaluate is called THEN null is returned`() {
        val window = TimeUnit.HOURS.toMillis(1)
        val signal = RecencySignal(recentWindowMillis = window)
        val candidate = candidateWithAges(window + 1L)

        val result = runTest { signal.evaluate(candidate) }

        assertNull(result)
    }

    @Test
    fun `GIVEN the default constructor arguments WHEN a RecencySignal is built THEN the recent window matches the documented default`() {
        val signal = RecencySignal()
        val candidate = candidateWithAges(TimeUnit.HOURS.toMillis(RecencySignal.RECENT_WINDOW_HOURS) + 1L)

        val result = runTest { signal.evaluate(candidate) }

        assertNull(result)
    }

    @Test
    fun `GIVEN all tabs were just accessed WHEN evaluate is called THEN the score is at its maximum`() {
        val signal = RecencySignal()
        val candidate = candidateWithAges(0L, 0L)

        val result = runTest { signal.evaluate(candidate) } as SuggestionSignal.Recency

        assertEquals(SuggestionSignal.MAX_SCORE, result.score, FLOAT_DELTA)
        assertEquals(0L, result.averageAgeMillis)
    }

    @Test
    fun `GIVEN the average tab age exceeds the recent window WHEN evaluate is called THEN null is returned`() {
        val signal = RecencySignal(recentWindowMillis = TimeUnit.HOURS.toMillis(1))
        val candidate = candidateWithAges(TimeUnit.HOURS.toMillis(2), TimeUnit.HOURS.toMillis(2))

        val result = runTest { signal.evaluate(candidate) }

        assertNull(result)
    }

    @Test
    fun `GIVEN tabs with different ages WHEN evaluate is called THEN the average age is used`() {
        val signal = RecencySignal(recentWindowMillis = TimeUnit.MINUTES.toMillis(10))
        val candidate = candidateWithAges(0L, TimeUnit.MINUTES.toMillis(2))

        val result = runTest { signal.evaluate(candidate) } as SuggestionSignal.Recency

        assertEquals(TimeUnit.MINUTES.toMillis(1), result.averageAgeMillis)
    }

    @Test
    fun `GIVEN an older average age WHEN evaluate is called THEN the score is lower than for a more recent one`() {
        val signal = RecencySignal(recentWindowMillis = TimeUnit.HOURS.toMillis(6))
        val recentCandidate = candidateWithAges(TimeUnit.MINUTES.toMillis(1))
        val olderCandidate = candidateWithAges(TimeUnit.HOURS.toMillis(3))

        val recentScore = (runTest { signal.evaluate(recentCandidate) } as SuggestionSignal.Recency).score
        val olderScore = (runTest { signal.evaluate(olderCandidate) } as SuggestionSignal.Recency).score

        assertTrue(recentScore > olderScore)
    }

    @Test
    fun `GIVEN three tabs with different ages WHEN evaluate is called THEN the mean of all three is used`() {
        val signal = RecencySignal(recentWindowMillis = TimeUnit.MINUTES.toMillis(30))
        val candidate = candidateWithAges(
            TimeUnit.MINUTES.toMillis(1),
            TimeUnit.MINUTES.toMillis(2),
            TimeUnit.MINUTES.toMillis(3),
        )

        val result = runTest { signal.evaluate(candidate) } as SuggestionSignal.Recency

        assertEquals(TimeUnit.MINUTES.toMillis(2), result.averageAgeMillis)
    }

    @Test
    fun `GIVEN a very short recent window WHEN evaluate is called THEN the score decays quickly with age`() {
        val signal = RecencySignal(recentWindowMillis = TimeUnit.SECONDS.toMillis(10))
        val nearlyAtWindowEdge = candidateWithAges(TimeUnit.SECONDS.toMillis(9))

        val result = runTest { signal.evaluate(nearlyAtWindowEdge) } as SuggestionSignal.Recency

        assertEquals(0.1f, result.score, FLOAT_DELTA)
    }

    @Test
    fun `GIVEN a custom recent window WHEN evaluate is called THEN the score is normalized against it`() {
        val signal = RecencySignal(recentWindowMillis = 1_000L)
        val candidate = candidateWithAges(500L)

        val result = runTest { signal.evaluate(candidate) } as SuggestionSignal.Recency

        assertEquals(0.5f, result.score, FLOAT_DELTA)
    }

    @Test
    fun `GIVEN a tab accessed after the reference time WHEN evaluate is called THEN the negative age is treated as zero`() {
        val signal = RecencySignal()
        val futureAccessTab = createTab(url = "https://mozilla.org", id = "1", lastAccess = NOW + 10_000L)
        val candidate = CandidateTabGroup(tabs = listOf(futureAccessTab), now = NOW)

        val result = runTest { signal.evaluate(candidate) } as SuggestionSignal.Recency

        assertEquals(0L, result.averageAgeMillis)
        assertEquals(SuggestionSignal.MAX_SCORE, result.score, FLOAT_DELTA)
    }

    @Test
    fun `GIVEN a matching candidate WHEN evaluate is called THEN the rationale mentions minutes since access`() {
        val signal = RecencySignal()
        val candidate = candidateWithAges(TimeUnit.MINUTES.toMillis(5))

        val result = runTest { signal.evaluate(candidate) } as SuggestionSignal.Recency

        assertEquals("Tabs were accessed 5 minutes ago on average", result.rationale)
    }

    private fun candidateWithAges(vararg agesMillis: Long): CandidateTabGroup {
        val tabs = agesMillis.mapIndexed { index, age ->
            createTab(url = "https://example.com/$index", id = index.toString(), lastAccess = NOW - age)
        }
        return CandidateTabGroup(tabs = tabs, now = NOW)
    }
}
