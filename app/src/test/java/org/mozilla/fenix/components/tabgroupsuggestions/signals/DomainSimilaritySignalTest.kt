/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.components.tabgroupsuggestions.signals

import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import mozilla.components.browser.state.state.createTab
import mozilla.components.lib.publicsuffixlist.PublicSuffixList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mozilla.fenix.components.tabgroupsuggestions.CandidateTabGroup
import org.mozilla.fenix.components.tabgroupsuggestions.SuggestionSignal
import org.mozilla.fenix.helpers.FenixRobolectricTestRunner

@RunWith(FenixRobolectricTestRunner::class)
class DomainSimilaritySignalTest {

    @MockK
    private lateinit var publicSuffixList: PublicSuffixList

    private lateinit var signal: DomainSimilaritySignal

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        signal = DomainSimilaritySignal(publicSuffixList)
    }

    @Test
    fun `GIVEN exactly two tabs WHEN evaluate is called THEN the minimum tab count requirement is satisfied`() {
        stub("mozilla.org", "mozilla.org")
        val candidate = candidateOf("mozilla.org", "mozilla.org")

        val result = runTest { signal.evaluate(candidate) }

        assertEquals(DomainSimilaritySignal.MIN_TABS_FOR_SIGNAL, candidate.tabs.size)
        assertEquals(true, result is SuggestionSignal.DomainSimilarity)
    }

    @Test
    fun `GIVEN a share ratio exactly at the minimum WHEN evaluate is called THEN a signal is still returned`() {
        stub("mozilla.org", "mozilla.org")
        stub("example.com", "example.com")
        val candidate = candidateOf("mozilla.org", "mozilla.org", "example.com", "example.com")

        val result = runTest { signal.evaluate(candidate) }

        assertEquals(true, result is SuggestionSignal.DomainSimilarity)
        assertEquals(DomainSimilaritySignal.MIN_SHARE_RATIO, (result as SuggestionSignal.DomainSimilarity).score, FLOAT_DELTA)
    }

    @Test
    fun `GIVEN fewer tabs than the minimum WHEN evaluate is called THEN null is returned`() {
        stub("mozilla.org", "mozilla.org")
        val candidate = candidateOf("mozilla.org")

        val result = runTest { signal.evaluate(candidate) }

        assertNull(result)
    }

    @Test
    fun `GIVEN a large group of tabs mostly on one domain WHEN evaluate is called THEN the share ratio still reflects the minority tabs`() {
        stub("mozilla.org", "mozilla.org")
        stub("example.com", "example.com")
        val candidate = candidateOf(
            "mozilla.org",
            "mozilla.org",
            "mozilla.org",
            "mozilla.org",
            "example.com",
        )

        val result = runTest { signal.evaluate(candidate) } as SuggestionSignal.DomainSimilarity

        assertEquals("mozilla.org", result.sharedRegistrableDomain)
        assertEquals(0.8f, result.score, FLOAT_DELTA)
    }

    @Test
    fun `GIVEN a tie between two equally represented domains WHEN evaluate is called THEN one of them is still reported`() {
        stub("mozilla.org", "mozilla.org")
        stub("example.com", "example.com")
        val candidate = candidateOf("mozilla.org", "example.com")

        val result = runTest { signal.evaluate(candidate) } as SuggestionSignal.DomainSimilarity

        assertEquals(true, result.sharedRegistrableDomain == "mozilla.org" || result.sharedRegistrableDomain == "example.com")
        assertEquals(0.5f, result.score, FLOAT_DELTA)
    }

    @Test
    fun `GIVEN all tabs share a domain WHEN evaluate is called THEN a signal is returned with a full score`() {
        stub("mozilla.org", "mozilla.org")
        val candidate = candidateOf("mozilla.org", "mozilla.org")

        val result = runTest { signal.evaluate(candidate) } as SuggestionSignal.DomainSimilarity

        assertEquals(1f, result.score, FLOAT_DELTA)
        assertEquals("mozilla.org", result.sharedRegistrableDomain)
    }

    @Test
    fun `GIVEN half the tabs share a domain WHEN evaluate is called THEN the score reflects the share ratio`() {
        stub("mozilla.org", "mozilla.org")
        stub("example.com", "example.com")
        val candidate = candidateOf("mozilla.org", "mozilla.org", "example.com", "example.com")

        val result = runTest { signal.evaluate(candidate) } as SuggestionSignal.DomainSimilarity

        assertEquals(0.5f, result.score, FLOAT_DELTA)
    }

    @Test
    fun `GIVEN the majority domain appears more than once WHEN evaluate is called THEN that domain is reported`() {
        stub("mozilla.org", "mozilla.org")
        stub("example.com", "example.com")
        val candidate = candidateOf("mozilla.org", "mozilla.org", "mozilla.org", "example.com")

        val result = runTest { signal.evaluate(candidate) } as SuggestionSignal.DomainSimilarity

        assertEquals("mozilla.org", result.sharedRegistrableDomain)
        assertEquals(0.75f, result.score, FLOAT_DELTA)
    }

    @Test
    fun `GIVEN the majority domain share ratio is below the threshold WHEN evaluate is called THEN null is returned`() {
        stub("a.com", "a.com")
        stub("b.com", "b.com")
        stub("c.com", "c.com")
        val candidate = candidateOf("a.com", "b.com", "c.com")

        val result = runTest { signal.evaluate(candidate) }

        assertNull(result)
    }

    @Test
    fun `GIVEN a blank domain has the most tabs WHEN evaluate is called THEN it is not reported as the shared domain`() {
        stub("", "")
        stub("mozilla.org", "mozilla.org")
        val candidate = candidateOf("", "", "mozilla.org")

        val result = runTest { signal.evaluate(candidate) }

        assertNull(result)
    }

    @Test
    fun `GIVEN a matching domain WHEN evaluate is called THEN the rationale mentions the tab counts and domain`() {
        stub("mozilla.org", "mozilla.org")
        val candidate = candidateOf("mozilla.org", "mozilla.org")

        val result = runTest { signal.evaluate(candidate) } as SuggestionSignal.DomainSimilarity

        assertEquals("2 of 2 tabs share the domain \"mozilla.org\"", result.rationale)
    }

    private fun stub(url: String, shortUrl: String) {
        every { publicSuffixList.stripPublicSuffix(url) } returns CompletableDeferred(shortUrl)
    }

    private fun candidateOf(vararg urls: String): CandidateTabGroup {
        val tabs = urls.mapIndexed { index, url -> createTab(url = url, id = index.toString()) }
        return CandidateTabGroup(tabs = tabs)
    }

    companion object {
        private const val FLOAT_DELTA = 0.001f
    }
}
