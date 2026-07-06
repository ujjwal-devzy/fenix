/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.components.tabgroupsuggestions.signals

import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.test.runTest
import mozilla.components.browser.state.state.createTab
import mozilla.components.concept.storage.HistoryStorage
import mozilla.components.concept.storage.VisitInfo
import mozilla.components.concept.storage.VisitType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.mozilla.fenix.components.tabgroupsuggestions.CandidateTabGroup
import org.mozilla.fenix.components.tabgroupsuggestions.SuggestionSignal
import java.util.concurrent.TimeUnit

private const val FLOAT_DELTA = 0.001f
private const val NOW = 1_000_000_000L

class FrequencySignalTest {

    @MockK
    private lateinit var historyStorage: HistoryStorage

    @Before
    fun setup() {
        MockKAnnotations.init(this)
    }

    @Test
    fun `GIVEN fewer tabs than the minimum WHEN evaluate is called THEN null is returned`() {
        val signal = FrequencySignal(historyStorage)
        val candidate = candidateOf("https://mozilla.org")

        val result = runTest { signal.evaluate(candidate) }

        assertNull(result)
    }

    @Test
    fun `GIVEN a custom lookback of a single day WHEN evaluate is called THEN only that day's visits are requested`() {
        val signal = FrequencySignal(historyStorage, lookbackDays = 1L)
        val url = "https://mozilla.org"
        val candidate = candidateOf(url, url)
        coEvery { historyStorage.getDetailedVisits(any(), any()) } returns List(3) { visit(url) }

        runTest { signal.evaluate(candidate) }

        coVerify(exactly = 1) {
            historyStorage.getDetailedVisits(start = NOW - TimeUnit.DAYS.toMillis(1), end = NOW)
        }
    }

    @Test
    fun `GIVEN no recorded visits WHEN evaluate is called THEN null is returned`() {
        val signal = FrequencySignal(historyStorage)
        val candidate = candidateOf("https://mozilla.org", "https://mozilla.org/firefox")
        coEvery { historyStorage.getDetailedVisits(any(), any()) } returns emptyList()

        val result = runTest { signal.evaluate(candidate) }

        assertNull(result)
    }

    @Test
    fun `GIVEN the average visit count is below the minimum WHEN evaluate is called THEN null is returned`() {
        val signal = FrequencySignal(historyStorage)
        val url = "https://mozilla.org"
        val candidate = candidateOf(url, url)
        coEvery { historyStorage.getDetailedVisits(any(), any()) } returns listOf(visit(url))

        val result = runTest { signal.evaluate(candidate) }

        assertNull(result)
    }

    @Test
    fun `GIVEN the average visit count is exactly the minimum WHEN evaluate is called THEN a signal is returned`() {
        val signal = FrequencySignal(historyStorage)
        val url = "https://mozilla.org"
        val candidate = candidateOf(url, url)
        coEvery { historyStorage.getDetailedVisits(any(), any()) } returns List(2) { visit(url) }

        val result = runTest { signal.evaluate(candidate) }

        assertEquals(true, result is SuggestionSignal.Frequency)
    }

    @Test
    fun `GIVEN the average visit count is exactly the maximum for a full score WHEN evaluate is called THEN the score is exactly the maximum`() {
        val signal = FrequencySignal(historyStorage)
        val url = "https://mozilla.org"
        val candidate = candidateOf(url, url)
        coEvery { historyStorage.getDetailedVisits(any(), any()) } returns List(10) { visit(url) }

        val result = runTest { signal.evaluate(candidate) } as SuggestionSignal.Frequency

        assertEquals(SuggestionSignal.MAX_SCORE, result.score, FLOAT_DELTA)
    }

    @Test
    fun `GIVEN tabs visited often enough WHEN evaluate is called THEN a signal is returned with the average visit count`() {
        val signal = FrequencySignal(historyStorage)
        val urlA = "https://mozilla.org"
        val urlB = "https://mozilla.org/firefox"
        val candidate = candidateOf(urlA, urlB)
        coEvery { historyStorage.getDetailedVisits(any(), any()) } returns
            List(4) { visit(urlA) } + List(2) { visit(urlB) }

        val result = runTest { signal.evaluate(candidate) } as SuggestionSignal.Frequency

        assertEquals(3, result.averageVisitCount)
    }

    @Test
    fun `GIVEN visits recorded outside the lookback window WHEN evaluate is called THEN they are not requested from storage`() {
        val lookbackDays = 7L
        val signal = FrequencySignal(historyStorage, lookbackDays = lookbackDays)
        val url = "https://mozilla.org"
        val candidate = candidateOf(url, url)
        coEvery { historyStorage.getDetailedVisits(any(), any()) } returns List(6) { visit(url) }

        runTest { signal.evaluate(candidate) }

        coVerify(exactly = 1) {
            historyStorage.getDetailedVisits(start = NOW - TimeUnit.DAYS.toMillis(lookbackDays), end = NOW)
        }
    }

    @Test
    fun `GIVEN three tabs sharing the same url WHEN evaluate is called THEN the visit count is applied to each of them`() {
        val signal = FrequencySignal(historyStorage)
        val url = "https://mozilla.org"
        val candidate = candidateOf(url, url, url)
        coEvery { historyStorage.getDetailedVisits(any(), any()) } returns List(6) { visit(url) }

        val result = runTest { signal.evaluate(candidate) } as SuggestionSignal.Frequency

        assertEquals(6, result.averageVisitCount)
    }

    @Test
    fun `GIVEN an average visit count within range WHEN evaluate is called THEN the score is proportional to it`() {
        val signal = FrequencySignal(historyStorage)
        val url = "https://mozilla.org"
        val candidate = candidateOf(url, url)
        coEvery { historyStorage.getDetailedVisits(any(), any()) } returns List(5) { visit(url) }

        val result = runTest { signal.evaluate(candidate) } as SuggestionSignal.Frequency

        // Average visit count is 5 (5 visits recorded for a url shared by both tabs), which is
        // half of MAX_VISITS_FOR_FULL_SCORE.
        assertEquals(0.5f, result.score, FLOAT_DELTA)
    }

    @Test
    fun `GIVEN an average visit count above the max WHEN evaluate is called THEN the score is coerced to the maximum`() {
        val signal = FrequencySignal(historyStorage)
        val url = "https://mozilla.org"
        val candidate = candidateOf(url, url)
        coEvery { historyStorage.getDetailedVisits(any(), any()) } returns List(50) { visit(url) }

        val result = runTest { signal.evaluate(candidate) } as SuggestionSignal.Frequency

        assertEquals(SuggestionSignal.MAX_SCORE, result.score, FLOAT_DELTA)
    }

    @Test
    fun `GIVEN a tab with no recorded visits alongside a frequently visited one WHEN evaluate is called THEN it counts as zero visits`() {
        val signal = FrequencySignal(historyStorage)
        val visitedUrl = "https://mozilla.org"
        val unvisitedUrl = "https://example.com"
        val candidate = candidateOf(visitedUrl, unvisitedUrl)
        coEvery { historyStorage.getDetailedVisits(any(), any()) } returns List(4) { visit(visitedUrl) }

        val result = runTest { signal.evaluate(candidate) } as SuggestionSignal.Frequency

        assertEquals(2, result.averageVisitCount)
    }

    @Test
    fun `GIVEN a custom lookback window WHEN evaluate is called THEN history is queried across that window`() {
        val lookbackDays = 30L
        val signal = FrequencySignal(historyStorage, lookbackDays = lookbackDays)
        val url = "https://mozilla.org"
        val candidate = CandidateTabGroup(tabs = listOf(createTab(url = url, id = "1"), createTab(url = url, id = "2")), now = NOW)
        coEvery { historyStorage.getDetailedVisits(any(), any()) } returns List(5) { visit(url) }

        runTest { signal.evaluate(candidate) }

        val expectedStart = NOW - TimeUnit.DAYS.toMillis(lookbackDays)
        coVerify { historyStorage.getDetailedVisits(start = expectedStart, end = NOW) }
    }

    @Test
    fun `GIVEN a matching candidate WHEN evaluate is called THEN the rationale mentions the average visit count`() {
        val signal = FrequencySignal(historyStorage, lookbackDays = 14L)
        val url = "https://mozilla.org"
        val candidate = candidateOf(url, url)
        coEvery { historyStorage.getDetailedVisits(any(), any()) } returns List(4) { visit(url) }

        val result = runTest { signal.evaluate(candidate) } as SuggestionSignal.Frequency

        assertEquals("Tabs were visited 4.0 times on average over the last 14 days", result.rationale)
    }

    private fun candidateOf(vararg urls: String): CandidateTabGroup {
        val tabs = urls.mapIndexed { index, url -> createTab(url = url, id = index.toString()) }
        return CandidateTabGroup(tabs = tabs, now = NOW)
    }

    private fun visit(url: String) = VisitInfo(
        url = url,
        title = "",
        visitTime = NOW,
        visitType = VisitType.LINK,
        previewImageUrl = null,
        isRemote = false,
    )
}
