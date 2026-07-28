/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.components.tabgroupsuggestions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TabGroupSuggestionDiffingTest {

    private val diffing = TabGroupSuggestionDiffing()

    @Test
    fun `GIVEN a mix of dismissed, unchanged and new suggestions WHEN diff is called THEN each is handled correctly`() {
        val dismissed = suggestion(id = "dismissed", tabIds = listOf("1", "2"), createdAt = 500L)
        val previousUnchanged = suggestion(id = "unchanged", tabIds = listOf("3", "4"), createdAt = 1_000L)
        val updatedUnchanged = suggestion(id = "unchanged", tabIds = listOf("3", "4"), createdAt = 5_000L)
        val brandNew = suggestion(id = "new", tabIds = listOf("5", "6"), createdAt = 9_000L)

        val result = diffing.diff(
            previous = listOf(dismissed, previousUnchanged),
            updated = listOf(dismissed, updatedUnchanged, brandNew),
            dismissedIds = setOf("dismissed"),
        )

        assertEquals(2, result.size)
        val unchangedResult = result.single { it.id == "unchanged" }
        val newResult = result.single { it.id == "new" }
        assertEquals(1_000L, unchangedResult.createdAt)
        assertEquals(9_000L, newResult.createdAt)
    }

    @Test
    fun `GIVEN a suggestion id in the dismissed set WHEN diff is called THEN it is excluded from the result`() {
        val dismissed = suggestion(id = "dismissed", tabIds = listOf("1", "2"))
        val kept = suggestion(id = "kept", tabIds = listOf("3", "4"))

        val result = diffing.diff(
            previous = emptyList(),
            updated = listOf(dismissed, kept),
            dismissedIds = setOf("dismissed"),
        )

        assertEquals(listOf(kept), result)
    }

    @Test
    fun `GIVEN no suggestions are dismissed WHEN diff is called THEN all updated suggestions are kept`() {
        val first = suggestion(id = "1", tabIds = listOf("1"))
        val second = suggestion(id = "2", tabIds = listOf("2"))

        val result = diffing.diff(previous = emptyList(), updated = listOf(first, second), dismissedIds = emptySet())

        assertEquals(listOf(first, second), result)
    }

    @Test
    fun `GIVEN an unchanged suggestion from the previous cycle WHEN diff is called THEN its original createdAt is preserved`() {
        val previousSuggestion = suggestion(id = "1", tabIds = listOf("1", "2"), createdAt = 1_000L)
        val freshSuggestion = suggestion(id = "1", tabIds = listOf("1", "2"), createdAt = 2_000L)

        val result = diffing.diff(
            previous = listOf(previousSuggestion),
            updated = listOf(freshSuggestion),
            dismissedIds = emptySet(),
        )

        assertEquals(1_000L, result.single().createdAt)
    }

    @Test
    fun `GIVEN a suggestion whose tabs changed WHEN diff is called THEN the fresh createdAt is used`() {
        val previousSuggestion = suggestion(id = "1", tabIds = listOf("1", "2"), createdAt = 1_000L)
        val freshSuggestion = suggestion(id = "1", tabIds = listOf("1", "3"), createdAt = 2_000L)

        val result = diffing.diff(
            previous = listOf(previousSuggestion),
            updated = listOf(freshSuggestion),
            dismissedIds = emptySet(),
        )

        assertEquals(2_000L, result.single().createdAt)
    }

    @Test
    fun `GIVEN a suggestion whose signals changed WHEN diff is called THEN the fresh createdAt is used`() {
        val previousSuggestion = suggestion(
            id = "1",
            tabIds = listOf("1", "2"),
            createdAt = 1_000L,
            signals = listOf(domainSignal(0.5f)),
        )
        val freshSuggestion = suggestion(
            id = "1",
            tabIds = listOf("1", "2"),
            createdAt = 2_000L,
            signals = listOf(domainSignal(0.9f)),
        )

        val result = diffing.diff(
            previous = listOf(previousSuggestion),
            updated = listOf(freshSuggestion),
            dismissedIds = emptySet(),
        )

        assertEquals(2_000L, result.single().createdAt)
    }

    @Test
    fun `GIVEN no previous suggestion with a matching id WHEN diff is called THEN the fresh createdAt is used`() {
        val freshSuggestion = suggestion(id = "1", tabIds = listOf("1", "2"), createdAt = 2_000L)

        val result = diffing.diff(previous = emptyList(), updated = listOf(freshSuggestion), dismissedIds = emptySet())

        assertEquals(2_000L, result.single().createdAt)
    }

    @Test
    fun `GIVEN previous is null WHEN reuseTimestampIfUnchanged is called THEN the candidate is returned unchanged`() {
        val candidate = suggestion(id = "1", tabIds = listOf("1"), createdAt = 5_000L)

        val result = diffing.reuseTimestampIfUnchanged(candidate, previous = null)

        assertEquals(candidate, result)
    }

    @Test
    fun `GIVEN identical tabs and signals WHEN reuseTimestampIfUnchanged is called THEN the previous createdAt is reused`() {
        val previous = suggestion(id = "1", tabIds = listOf("1", "2"), createdAt = 1_000L)
        val candidate = suggestion(id = "1", tabIds = listOf("1", "2"), createdAt = 9_000L)

        val result = diffing.reuseTimestampIfUnchanged(candidate, previous)

        assertEquals(1_000L, result.createdAt)
    }

    @Test
    fun `GIVEN the same tab ids in a different order WHEN reuseTimestampIfUnchanged is called THEN they are still considered unchanged`() {
        val previous = suggestion(id = "1", tabIds = listOf("1", "2"), createdAt = 1_000L)
        val candidate = suggestion(id = "1", tabIds = listOf("2", "1"), createdAt = 9_000L)

        val result = diffing.reuseTimestampIfUnchanged(candidate, previous)

        assertEquals(1_000L, result.createdAt)
    }

    @Test
    fun `GIVEN different signals WHEN reuseTimestampIfUnchanged is called THEN the candidate's own createdAt is kept`() {
        val previous = suggestion(
            id = "1",
            tabIds = listOf("1", "2"),
            createdAt = 1_000L,
            signals = listOf(domainSignal(0.4f)),
        )
        val candidate = suggestion(
            id = "1",
            tabIds = listOf("1", "2"),
            createdAt = 9_000L,
            signals = listOf(domainSignal(0.6f)),
        )

        val result = diffing.reuseTimestampIfUnchanged(candidate, previous)

        assertEquals(9_000L, result.createdAt)
        assertTrue(result === candidate || result == candidate)
    }

    @Test
    fun `GIVEN an extra tab was added to a suggestion WHEN reuseTimestampIfUnchanged is called THEN it is treated as changed`() {
        val previous = suggestion(id = "1", tabIds = listOf("1", "2"), createdAt = 1_000L)
        val candidate = suggestion(id = "1", tabIds = listOf("1", "2", "3"), createdAt = 9_000L)

        val result = diffing.reuseTimestampIfUnchanged(candidate, previous)

        assertEquals(9_000L, result.createdAt)
    }

    @Test
    fun `GIVEN multiple previous suggestions WHEN diff is called THEN only the matching one contributes its createdAt`() {
        val unrelatedPrevious = suggestion(id = "unrelated", tabIds = listOf("9"), createdAt = 500L)
        val matchingPrevious = suggestion(id = "1", tabIds = listOf("1", "2"), createdAt = 1_000L)
        val freshSuggestion = suggestion(id = "1", tabIds = listOf("1", "2"), createdAt = 5_000L)

        val result = diffing.diff(
            previous = listOf(unrelatedPrevious, matchingPrevious),
            updated = listOf(freshSuggestion),
            dismissedIds = emptySet(),
        )

        assertEquals(1_000L, result.single().createdAt)
    }

    private fun suggestion(
        id: String,
        tabIds: List<String>,
        signals: List<SuggestionSignal> = emptyList(),
        createdAt: Long = 0L,
    ) = TabGroupSuggestion(
        id = id,
        tabIds = tabIds,
        suggestedName = "Suggestion $id",
        score = 0.5f,
        signals = signals,
        createdAt = createdAt,
    )

    private fun domainSignal(score: Float) = SuggestionSignal.DomainSimilarity(
        score = score,
        rationale = "shared domain",
        sharedRegistrableDomain = "example.com",
    )
}
