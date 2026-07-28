/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.components.tabgroupsuggestions

import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import mozilla.components.browser.state.state.createTab
import mozilla.components.concept.storage.HistoryStorage
import mozilla.components.lib.publicsuffixlist.PublicSuffixList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mozilla.fenix.components.tabgroupsuggestions.signals.RecencySignal
import org.mozilla.fenix.helpers.FenixRobolectricTestRunner
import java.util.concurrent.TimeUnit

private const val NOW = 1_000_000_000L
private const val DOMAIN = "mozilla.org"

@RunWith(FenixRobolectricTestRunner::class)
class TabGroupSuggestionEngineTest {

    @MockK
    private lateinit var publicSuffixList: PublicSuffixList

    @MockK(relaxed = true)
    private lateinit var historyStorage: HistoryStorage

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        every { publicSuffixList.stripPublicSuffix(DOMAIN) } returns CompletableDeferred(DOMAIN)
    }

    @Test
    fun `GIVEN no open tabs WHEN suggest is called THEN no suggestions are returned`() {
        val engine = buildEngine()

        val result = runTest { engine.suggest(emptyList()) }

        assertEquals(emptyList<TabGroupSuggestion>(), result)
    }

    @Test
    fun `GIVEN a single open tab WHEN suggest is called THEN no suggestions are returned`() {
        val engine = buildEngine()
        val tabs = listOf(createTab(url = DOMAIN, id = "1", lastAccess = NOW))

        val result = runTest { engine.suggest(tabs) }

        assertEquals(emptyList<TabGroupSuggestion>(), result)
    }

    @Test
    fun `GIVEN only tabs on distinct domains WHEN suggest is called THEN no suggestions are returned`() {
        val secondDomain = "example.com"
        every { publicSuffixList.stripPublicSuffix(secondDomain) } returns CompletableDeferred(secondDomain)

        val engine = buildEngine()
        val tabs = listOf(
            createTab(url = DOMAIN, id = "1", lastAccess = NOW),
            createTab(url = secondDomain, id = "2", lastAccess = NOW),
        )

        val result = runTest { engine.suggest(tabs) }

        assertEquals(emptyList<TabGroupSuggestion>(), result)
    }

    @Test
    fun `GIVEN tabs sharing a domain and accessed recently WHEN suggest is called THEN a suggestion is returned`() {
        val engine = buildEngine()
        val tabs = listOf(
            createTab(url = DOMAIN, id = "1", lastAccess = NOW, title = "Mozilla Home"),
            createTab(url = DOMAIN, id = "2", lastAccess = NOW, title = "Mozilla Blog"),
        )

        val result = runTest { engine.suggest(tabs) }

        assertEquals(1, result.size)
        assertEquals(listOf("1", "2"), result.single().tabIds)
        assertEquals(DOMAIN, result.single().suggestedName)
    }

    @Test
    fun `GIVEN two independent groups of tabs on different domains WHEN suggest is called THEN a suggestion is returned for each`() {
        val secondDomain = "example.com"
        every { publicSuffixList.stripPublicSuffix(secondDomain) } returns CompletableDeferred(secondDomain)

        val engine = buildEngine()
        val tabs = listOf(
            createTab(url = DOMAIN, id = "1", lastAccess = NOW, title = "Mozilla Home"),
            createTab(url = DOMAIN, id = "2", lastAccess = NOW, title = "Mozilla Blog"),
            createTab(url = secondDomain, id = "3", lastAccess = NOW, title = "Example Home"),
            createTab(url = secondDomain, id = "4", lastAccess = NOW, title = "Example Blog"),
        )

        val result = runTest { engine.suggest(tabs) }

        assertEquals(2, result.size)
        assertEquals(setOf(DOMAIN, secondDomain), result.map { it.suggestedName }.toSet())
    }

    @Test
    fun `GIVEN more candidates than the ranker's cap WHEN suggest is called THEN the result is limited accordingly`() {
        val ranker = SuggestionRanker(maxSuggestions = 1, minimumScore = 0f)
        val secondDomain = "example.com"
        every { publicSuffixList.stripPublicSuffix(secondDomain) } returns CompletableDeferred(secondDomain)

        val engine = TabGroupSuggestionEngine(
            publicSuffixList = publicSuffixList,
            historyStorage = historyStorage,
            ranker = ranker,
            clock = { NOW },
        )
        val tabs = listOf(
            createTab(url = DOMAIN, id = "1", lastAccess = NOW, title = "Mozilla Home"),
            createTab(url = DOMAIN, id = "2", lastAccess = NOW, title = "Mozilla Blog"),
            createTab(url = secondDomain, id = "3", lastAccess = NOW, title = "Example Home"),
            createTab(url = secondDomain, id = "4", lastAccess = NOW, title = "Example Blog"),
        )

        val result = runTest { engine.suggest(tabs) }

        assertEquals(1, result.size)
    }

    @Test
    fun `GIVEN the down-weighted domain signal is the only one contributing meaningfully WHEN suggest is called THEN it does not clear the meaningful score threshold`() {
        val weakDomain = "shared-host.example"
        every { publicSuffixList.stripPublicSuffix(weakDomain) } returns CompletableDeferred(weakDomain)

        val engine = TabGroupSuggestionEngine(
            publicSuffixList = publicSuffixList,
            historyStorage = historyStorage,
            // Recency dominates the weighted average here, so even though domain similarity
            // reports a perfect score for tabs sharing a domain, it is not enough on its own to
            // clear the ranker's default MEANINGFUL_SCORE_THRESHOLD.
            weights = SuggestionWeights(
                domainSimilarity = 0.01f,
                recency = 0.97f,
                frequency = 0.01f,
                titleKeywordOverlap = 0.01f,
            ),
            clock = { NOW },
        )
        val staleAge = TimeUnit.HOURS.toMillis(RecencySignal.RECENT_WINDOW_HOURS) - 1_000L
        val tabs = listOf(
            createTab(url = weakDomain, id = "1", lastAccess = NOW - staleAge, title = "Weather"),
            createTab(url = weakDomain, id = "2", lastAccess = NOW - staleAge, title = "Recipe"),
        )

        val result = runTest { engine.suggest(tabs) }

        assertEquals(emptyList<TabGroupSuggestion>(), result)
    }

    @Test
    fun `GIVEN private tabs sharing a domain WHEN suggest is called THEN no suggestions are returned`() {
        val engine = buildEngine()
        val tabs = listOf(
            createTab(url = DOMAIN, id = "1", lastAccess = NOW, private = true),
            createTab(url = DOMAIN, id = "2", lastAccess = NOW, private = true),
        )

        val result = runTest { engine.suggest(tabs) }

        assertEquals(emptyList<TabGroupSuggestion>(), result)
    }

    @Test
    fun `GIVEN a suggestion is produced WHEN suggest is called THEN createdAt uses the injected clock`() {
        val engine = buildEngine()
        val tabs = listOf(
            createTab(url = DOMAIN, id = "1", lastAccess = NOW),
            createTab(url = DOMAIN, id = "2", lastAccess = NOW),
        )

        val result = runTest { engine.suggest(tabs) }

        assertEquals(NOW, result.single().createdAt)
    }

    @Test
    fun `GIVEN tabs sharing a domain WHEN buildCandidateGroups is called THEN they are grouped into one candidate`() {
        val engine = buildEngine()
        val tabs = listOf(
            createTab(url = DOMAIN, id = "1"),
            createTab(url = DOMAIN, id = "2"),
        )

        val result = engine.buildCandidateGroups(tabs, NOW)

        assertEquals(1, result.size)
        assertEquals(2, result.single().tabs.size)
    }

    @Test
    fun `GIVEN a domain shared by only one tab WHEN buildCandidateGroups is called THEN it is excluded`() {
        val engine = buildEngine()
        val otherDomain = "example.com"
        every { publicSuffixList.stripPublicSuffix(otherDomain) } returns CompletableDeferred(otherDomain)

        val tabs = listOf(
            createTab(url = DOMAIN, id = "1"),
            createTab(url = DOMAIN, id = "2"),
            createTab(url = otherDomain, id = "3"),
        )

        val result = engine.buildCandidateGroups(tabs, NOW)

        assertEquals(1, result.size)
        assertEquals(listOf("1", "2"), result.single().tabs.map { it.id })
    }

    @Test
    fun `GIVEN private tabs sharing a domain WHEN buildCandidateGroups is called THEN they are excluded`() {
        val engine = buildEngine()
        val tabs = listOf(
            createTab(url = DOMAIN, id = "1", private = true),
            createTab(url = DOMAIN, id = "2", private = true),
        )

        val result = engine.buildCandidateGroups(tabs, NOW)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `GIVEN tabs whose short url is blank WHEN buildCandidateGroups is called THEN they are excluded`() {
        val engine = buildEngine()
        val blankUrl = "about:blank"
        every { publicSuffixList.stripPublicSuffix(blankUrl) } returns CompletableDeferred("")

        val tabs = listOf(
            createTab(url = blankUrl, id = "1"),
            createTab(url = blankUrl, id = "2"),
        )

        val result = engine.buildCandidateGroups(tabs, NOW)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `WHEN suggest is called THEN every configured signal is evaluated for each candidate`() {
        val fakeSignal = mockk<CandidateGroupSignal>()
        coEvery { fakeSignal.evaluate(any()) } returns null

        val engine = TabGroupSuggestionEngine(
            publicSuffixList = publicSuffixList,
            historyStorage = historyStorage,
            signals = listOf(fakeSignal),
            clock = { NOW },
        )
        val tabs = listOf(
            createTab(url = DOMAIN, id = "1", lastAccess = NOW),
            createTab(url = DOMAIN, id = "2", lastAccess = NOW),
        )

        runTest { engine.suggest(tabs) }

        coVerify(exactly = 1) { fakeSignal.evaluate(any()) }
    }

    @Test
    fun `WHEN suggest is called THEN scoring is delegated to the provided scorer for each candidate`() {
        val scorer = mockk<SuggestionScorer>()
        every { scorer.scoreCandidate(any(), any()) } returns ScoredCandidate(
            candidate = CandidateTabGroup(tabs = emptyList()),
            signals = emptyList(),
            score = 0f,
        )

        val engine = TabGroupSuggestionEngine(
            publicSuffixList = publicSuffixList,
            historyStorage = historyStorage,
            scorer = scorer,
            clock = { NOW },
        )
        val tabs = listOf(
            createTab(url = DOMAIN, id = "1", lastAccess = NOW),
            createTab(url = DOMAIN, id = "2", lastAccess = NOW),
        )

        runTest { engine.suggest(tabs) }

        verify(exactly = 1) { scorer.scoreCandidate(any(), any()) }
    }

    @Test
    fun `WHEN suggest is called THEN ranking is delegated to the provided ranker using the clock's timestamp`() {
        val ranker = mockk<SuggestionRanker>()
        every { ranker.rank(any(), NOW) } returns emptyList()

        val engine = TabGroupSuggestionEngine(
            publicSuffixList = publicSuffixList,
            historyStorage = historyStorage,
            ranker = ranker,
            clock = { NOW },
        )
        val tabs = listOf(
            createTab(url = DOMAIN, id = "1", lastAccess = NOW),
            createTab(url = DOMAIN, id = "2", lastAccess = NOW),
        )

        runTest { engine.suggest(tabs) }

        verify(exactly = 1) { ranker.rank(any(), NOW) }
    }

    private fun buildEngine(): TabGroupSuggestionEngine {
        return TabGroupSuggestionEngine(
            publicSuffixList = publicSuffixList,
            historyStorage = historyStorage,
            clock = { NOW },
        )
    }
}
