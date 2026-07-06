/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.components.tabgroupsuggestions.store

/**
 * Reduces [TabGroupSuggestionAction]s dispatched to [TabGroupSuggestionStore] into a new
 * [TabGroupSuggestionState].
 *
 * Kept as a standalone object, rather than a method on [TabGroupSuggestionState] or
 * [TabGroupSuggestionStore], to match the reducer pattern used by
 * `org.mozilla.fenix.components.appstate.AppStoreReducer` elsewhere in this codebase.
 */
internal object TabGroupSuggestionReducer {

    /**
     * Reduces [action] against [state], returning the resulting [TabGroupSuggestionState].
     */
    fun reduce(state: TabGroupSuggestionState, action: TabGroupSuggestionAction): TabGroupSuggestionState {
        return when (action) {
            is TabGroupSuggestionAction.SuggestionsUpdated -> state.copy(
                suggestions = action.suggestions,
                status = TabGroupSuggestionStatus.Idle,
            )

            is TabGroupSuggestionAction.SuggestionAccepted -> state.copy(
                suggestions = state.suggestions.filterNot { suggestion -> suggestion.id == action.suggestionId },
            )

            is TabGroupSuggestionAction.SuggestionDismissed -> state.copy(
                suggestions = state.suggestions.filterNot { suggestion -> suggestion.id == action.suggestionId },
                dismissedSuggestionIds = state.dismissedSuggestionIds + action.suggestionId,
            )

            is TabGroupSuggestionAction.OptOutToggled -> state.copy(
                isEnabled = action.enabled,
                suggestions = if (action.enabled) state.suggestions else emptyList(),
            )

            TabGroupSuggestionAction.RefreshStarted -> state.copy(
                status = TabGroupSuggestionStatus.Refreshing,
            )
        }
    }
}
