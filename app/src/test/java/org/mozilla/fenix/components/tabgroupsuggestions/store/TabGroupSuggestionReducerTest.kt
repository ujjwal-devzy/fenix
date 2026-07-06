/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.components.tabgroupsuggestions.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mozilla.fenix.components.tabgroupsuggestions.TabGroupSuggestion

class TabGroupSuggestionReducerTest {

    @Test
    fun `GIVEN a Refreshing status WHEN RefreshStarted is reduced again THEN the status remains Refreshing`() {
        val state = TabGroupSuggestionState(status = TabGroupSuggestionStatus.Refreshing)

        val result = TabGroupSuggestionReducer.reduce(state, TabGroupSuggestionAction.RefreshStarted)

        assertEquals(TabGroupSuggestionStatus.Refreshing, result.status)
    }

    @Test
    fun `GIVEN RefreshStarted WHEN reduced THEN existing suggestions are left in place until the refresh completes`() {
        val existingSuggestions = listOf(suggestion("1"))
        val state = TabGroupSuggestionState(suggestions = existingSuggestions)

        val result = TabGroupSuggestionReducer.reduce(state, TabGroupSuggestionAction.RefreshStarted)

        assertEquals(existingSuggestions, result.suggestions)
    }

    @Test
    fun `GIVEN RefreshStarted WHEN reduced THEN the status becomes Refreshing`() {
        val state = TabGroupSuggestionState(status = TabGroupSuggestionStatus.Idle)

        val result = TabGroupSuggestionReducer.reduce(state, TabGroupSuggestionAction.RefreshStarted)

        assertEquals(TabGroupSuggestionStatus.Refreshing, result.status)
    }

    @Test
    fun `GIVEN SuggestionsUpdated WHEN reduced THEN suggestions are replaced and status returns to Idle`() {
        val state = TabGroupSuggestionState(
            suggestions = listOf(suggestion("stale")),
            status = TabGroupSuggestionStatus.Refreshing,
        )
        val freshSuggestions = listOf(suggestion("fresh-1"), suggestion("fresh-2"))

        val result = TabGroupSuggestionReducer.reduce(
            state,
            TabGroupSuggestionAction.SuggestionsUpdated(freshSuggestions),
        )

        assertEquals(freshSuggestions, result.suggestions)
        assertEquals(TabGroupSuggestionStatus.Idle, result.status)
    }

    @Test
    fun `GIVEN a matching suggestion WHEN SuggestionAccepted is reduced THEN it is removed from the suggestions list`() {
        val accepted = suggestion("accepted")
        val other = suggestion("other")
        val state = TabGroupSuggestionState(suggestions = listOf(accepted, other))

        val result = TabGroupSuggestionReducer.reduce(
            state,
            TabGroupSuggestionAction.SuggestionAccepted(accepted.id),
        )

        assertEquals(listOf(other), result.suggestions)
    }

    @Test
    fun `GIVEN SuggestionAccepted for an unknown id WHEN reduced THEN the suggestions list is unchanged`() {
        val state = TabGroupSuggestionState(suggestions = listOf(suggestion("1"), suggestion("2")))

        val result = TabGroupSuggestionReducer.reduce(
            state,
            TabGroupSuggestionAction.SuggestionAccepted("unknown-id"),
        )

        assertEquals(state.suggestions, result.suggestions)
    }

    @Test
    fun `GIVEN SuggestionDismissed for an id no longer in the suggestions list WHEN reduced THEN it is still recorded as dismissed`() {
        val state = TabGroupSuggestionState(suggestions = emptyList())

        val result = TabGroupSuggestionReducer.reduce(
            state,
            TabGroupSuggestionAction.SuggestionDismissed("already-gone"),
        )

        assertTrue(result.suggestions.isEmpty())
        assertEquals(setOf("already-gone"), result.dismissedSuggestionIds)
    }

    @Test
    fun `GIVEN SuggestionAccepted WHEN reduced THEN dismissedSuggestionIds is left untouched`() {
        val state = TabGroupSuggestionState(
            suggestions = listOf(suggestion("1")),
            dismissedSuggestionIds = setOf("previously-dismissed"),
        )

        val result = TabGroupSuggestionReducer.reduce(
            state,
            TabGroupSuggestionAction.SuggestionAccepted("1"),
        )

        assertEquals(setOf("previously-dismissed"), result.dismissedSuggestionIds)
    }

    @Test
    fun `GIVEN a matching suggestion WHEN SuggestionDismissed is reduced THEN it is removed and marked dismissed`() {
        val dismissed = suggestion("dismissed")
        val other = suggestion("other")
        val state = TabGroupSuggestionState(suggestions = listOf(dismissed, other))

        val result = TabGroupSuggestionReducer.reduce(
            state,
            TabGroupSuggestionAction.SuggestionDismissed(dismissed.id),
        )

        assertEquals(listOf(other), result.suggestions)
        assertEquals(setOf("dismissed"), result.dismissedSuggestionIds)
    }

    @Test
    fun `GIVEN a suggestion id already marked dismissed WHEN SuggestionDismissed is reduced again THEN the set still contains it once`() {
        val state = TabGroupSuggestionState(
            suggestions = emptyList(),
            dismissedSuggestionIds = setOf("already-dismissed"),
        )

        val result = TabGroupSuggestionReducer.reduce(
            state,
            TabGroupSuggestionAction.SuggestionDismissed("already-dismissed"),
        )

        assertEquals(setOf("already-dismissed"), result.dismissedSuggestionIds)
    }

    @Test
    fun `GIVEN suggestions are currently shown WHEN OptOutToggled false is reduced THEN isEnabled becomes false and suggestions are cleared`() {
        val state = TabGroupSuggestionState(
            isEnabled = true,
            suggestions = listOf(suggestion("1"), suggestion("2")),
        )

        val result = TabGroupSuggestionReducer.reduce(
            state,
            TabGroupSuggestionAction.OptOutToggled(enabled = false),
        )

        assertFalse(result.isEnabled)
        assertTrue(result.suggestions.isEmpty())
    }

    @Test
    fun `GIVEN the feature is off WHEN OptOutToggled true is reduced THEN isEnabled becomes true and any existing suggestions are preserved`() {
        val existingSuggestions = listOf(suggestion("1"))
        val state = TabGroupSuggestionState(isEnabled = false, suggestions = existingSuggestions)

        val result = TabGroupSuggestionReducer.reduce(
            state,
            TabGroupSuggestionAction.OptOutToggled(enabled = true),
        )

        assertTrue(result.isEnabled)
        assertEquals(existingSuggestions, result.suggestions)
    }

    @Test
    fun `GIVEN a sequence of actions WHEN each is reduced in turn THEN the resulting state reflects all of them`() {
        var state = TabGroupSuggestionState()

        state = TabGroupSuggestionReducer.reduce(state, TabGroupSuggestionAction.RefreshStarted)
        assertEquals(TabGroupSuggestionStatus.Refreshing, state.status)

        val suggestions = listOf(suggestion("1"), suggestion("2"))
        state = TabGroupSuggestionReducer.reduce(state, TabGroupSuggestionAction.SuggestionsUpdated(suggestions))
        assertEquals(suggestions, state.suggestions)

        state = TabGroupSuggestionReducer.reduce(state, TabGroupSuggestionAction.SuggestionDismissed("1"))
        assertEquals(listOf(suggestion("2")), state.suggestions)
        assertEquals(setOf("1"), state.dismissedSuggestionIds)

        state = TabGroupSuggestionReducer.reduce(state, TabGroupSuggestionAction.OptOutToggled(enabled = false))
        assertTrue(state.suggestions.isEmpty())
        assertFalse(state.isEnabled)
    }

    private fun suggestion(id: String) = TabGroupSuggestion(
        id = id,
        tabIds = listOf(id),
        suggestedName = "Suggestion $id",
        score = 0.5f,
        signals = emptyList(),
        createdAt = 0L,
    )
}
