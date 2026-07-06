/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.components.tabgroupsuggestions.signals

import kotlinx.coroutines.test.runTest
import mozilla.components.browser.state.state.createTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mozilla.fenix.components.tabgroupsuggestions.CandidateTabGroup
import org.mozilla.fenix.components.tabgroupsuggestions.SuggestionSignal

private const val FLOAT_DELTA = 0.001f

class TitleKeywordSignalTest {

    @Test
    fun `GIVEN fewer tabs than the minimum WHEN evaluate is called THEN null is returned`() {
        val signal = TitleKeywordSignal()
        val candidate = candidateOf("Android Studio Release Notes")

        val result = runTest { signal.evaluate(candidate) }

        assertNull(result)
    }

    @Test
    fun `GIVEN a tab title with no meaningful keywords WHEN evaluate is called THEN null is returned`() {
        val signal = TitleKeywordSignal()
        val candidate = candidateOf("To Is At", "Android Studio Release Notes")

        val result = runTest { signal.evaluate(candidate) }

        assertNull(result)
    }

    @Test
    fun `GIVEN titles with no keywords in common WHEN evaluate is called THEN null is returned`() {
        val signal = TitleKeywordSignal()
        val candidate = candidateOf("Alpha Bravo Charlie", "Delta Echo Foxtrot")

        val result = runTest { signal.evaluate(candidate) }

        assertNull(result)
    }

    @Test
    fun `GIVEN a shared keyword ratio below the minimum WHEN evaluate is called THEN null is returned`() {
        val signal = TitleKeywordSignal()
        val candidate = candidateOf("Alpha Bravo Charlie Delta Echo Foxtrot", "Golf Hotel India Juliet Kilo Alpha")

        val result = runTest { signal.evaluate(candidate) }

        assertNull(result)
    }

    @Test
    fun `GIVEN a shared keyword ratio exactly at the minimum WHEN evaluate is called THEN a signal is still returned`() {
        val signal = TitleKeywordSignal()
        val candidate = candidateOf("Alpha Bravo Charlie", "Alpha Delta Echo")

        val result = runTest { signal.evaluate(candidate) } as SuggestionSignal.TitleKeywordOverlap

        assertEquals(TitleKeywordSignal.MIN_OVERLAP_RATIO, result.score, FLOAT_DELTA)
        assertEquals(listOf("alpha"), result.sharedKeywords)
    }

    @Test
    fun `GIVEN three tabs with one keyword in common WHEN evaluate is called THEN the shared keyword is reported`() {
        val signal = TitleKeywordSignal()
        val candidate = candidateOf("Android Studio Release", "Android Studio Guide", "Android Setup Guide")

        val result = runTest { signal.evaluate(candidate) } as SuggestionSignal.TitleKeywordOverlap

        assertEquals(listOf("android"), result.sharedKeywords)
    }

    @Test
    fun `GIVEN titles sharing some keywords WHEN evaluate is called THEN a signal is returned with the overlap ratio as the score`() {
        val signal = TitleKeywordSignal()
        val candidate = candidateOf("Android Studio Release Notes", "New Android Release Guide")

        val result = runTest { signal.evaluate(candidate) } as SuggestionSignal.TitleKeywordOverlap

        assertEquals(0.4f, result.score, FLOAT_DELTA)
        assertEquals(listOf("android", "release"), result.sharedKeywords)
    }

    @Test
    fun `GIVEN titles that only differ in case WHEN evaluate is called THEN they are still treated as matching keywords`() {
        val signal = TitleKeywordSignal()
        val candidate = candidateOf("ANDROID STUDIO", "android studio")

        val result = runTest { signal.evaluate(candidate) } as SuggestionSignal.TitleKeywordOverlap

        assertEquals(SuggestionSignal.MAX_SCORE, result.score, FLOAT_DELTA)
    }

    @Test
    fun `GIVEN titles containing punctuation around shared keywords WHEN evaluate is called THEN they are still matched`() {
        val signal = TitleKeywordSignal()
        val candidate = candidateOf("Android Studio: Release Notes!", "Android Studio - New Guide.")

        val result = runTest { signal.evaluate(candidate) } as SuggestionSignal.TitleKeywordOverlap

        assertEquals(listOf("android", "studio"), result.sharedKeywords)
    }

    @Test
    fun `GIVEN identical titles WHEN evaluate is called THEN the score is the maximum`() {
        val signal = TitleKeywordSignal()
        val candidate = candidateOf("Android Studio Release Notes", "Android Studio Release Notes")

        val result = runTest { signal.evaluate(candidate) } as SuggestionSignal.TitleKeywordOverlap

        assertEquals(SuggestionSignal.MAX_SCORE, result.score, FLOAT_DELTA)
    }

    @Test
    fun `GIVEN a matching candidate WHEN evaluate is called THEN sharedKeywords is sorted alphabetically`() {
        val signal = TitleKeywordSignal()
        val candidate = candidateOf("Zebra Yankee Android", "Android Zebra Yankee")

        val result = runTest { signal.evaluate(candidate) } as SuggestionSignal.TitleKeywordOverlap

        assertEquals(listOf("android", "yankee", "zebra"), result.sharedKeywords)
    }

    @Test
    fun `GIVEN a matching candidate WHEN evaluate is called THEN the rationale lists the shared keywords`() {
        val signal = TitleKeywordSignal()
        val candidate = candidateOf("Android Studio Release Notes", "New Android Release Guide")

        val result = runTest { signal.evaluate(candidate) } as SuggestionSignal.TitleKeywordOverlap

        assertEquals("Titles share the keyword(s): android, release", result.rationale)
    }

    @Test
    fun `WHEN extractKeywords is called on an empty title THEN an empty set is returned`() {
        val signal = TitleKeywordSignal()

        assertEquals(emptySet<String>(), signal.extractKeywords(""))
    }

    @Test
    fun `WHEN extractKeywords is called THEN tokens are lowercased`() {
        val signal = TitleKeywordSignal()

        assertEquals(setOf("android"), signal.extractKeywords("ANDROID"))
    }

    @Test
    fun `WHEN extractKeywords is called THEN tokens shorter than the minimum length are filtered out`() {
        val signal = TitleKeywordSignal()

        assertEquals(setOf("android"), signal.extractKeywords("go to android"))
    }

    @Test
    fun `WHEN extractKeywords is called THEN default stop words are filtered out`() {
        val signal = TitleKeywordSignal()

        assertEquals(setOf("android", "guide"), signal.extractKeywords("the android guide for you"))
    }

    @Test
    fun `WHEN extractKeywords is called THEN it splits on non-alphanumeric delimiters`() {
        val signal = TitleKeywordSignal()

        assertEquals(
            setOf("android", "studio", "release", "notes"),
            signal.extractKeywords("android-studio_release/notes"),
        )
    }

    @Test
    fun `GIVEN a custom stop word set WHEN extractKeywords is called THEN it is used instead of the defaults`() {
        val signal = TitleKeywordSignal(stopWords = setOf("android"))

        assertEquals(setOf("studio"), signal.extractKeywords("android studio"))
    }

    private fun candidateOf(vararg titles: String): CandidateTabGroup {
        val tabs = titles.mapIndexed { index, title ->
            createTab(url = "https://example.com/$index", id = index.toString(), title = title)
        }
        return CandidateTabGroup(tabs = tabs)
    }
}
