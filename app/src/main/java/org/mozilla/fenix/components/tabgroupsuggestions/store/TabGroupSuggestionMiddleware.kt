/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.components.tabgroupsuggestions.store

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import mozilla.components.lib.state.Middleware
import mozilla.components.lib.state.MiddlewareContext
import org.mozilla.fenix.components.tabgroupsuggestions.TabGroupSuggestion
import org.mozilla.fenix.components.tabgroupsuggestions.TabGroupSuggestionRepository
import org.mozilla.fenix.components.tabgroupsuggestions.TabGroupSuggestionTelemetry

/**
 * [Middleware] that dispatches telemetry and persistence side effects in response to
 * [TabGroupSuggestionAction]s, keeping [TabGroupSuggestionReducer] a pure function of state and
 * action.
 *
 * @property repository Used to persist dismissals and shown timestamps.
 * @property telemetry Used to record shown/accepted/dismissed events.
 * @property scope Scope used to run the suspending [repository] calls triggered by this
 * middleware.
 */
class TabGroupSuggestionMiddleware(
    private val repository: TabGroupSuggestionRepository,
    private val telemetry: TabGroupSuggestionTelemetry,
    private val scope: CoroutineScope,
) : Middleware<TabGroupSuggestionState, TabGroupSuggestionAction> {

    override fun invoke(
        context: MiddlewareContext<TabGroupSuggestionState, TabGroupSuggestionAction>,
        next: (TabGroupSuggestionAction) -> Unit,
        action: TabGroupSuggestionAction,
    ) {
        // The suggestion being acted on has to be looked up before calling `next`, since the
        // reducer removes it from `context.state.suggestions` as part of handling both
        // `SuggestionAccepted` and `SuggestionDismissed`.
        val suggestionBeingActedOn = when (action) {
            is TabGroupSuggestionAction.SuggestionAccepted ->
                context.state.suggestions.find { suggestion -> suggestion.id == action.suggestionId }
            is TabGroupSuggestionAction.SuggestionDismissed ->
                context.state.suggestions.find { suggestion -> suggestion.id == action.suggestionId }
            else -> null
        }

        next(action)

        when (action) {
            is TabGroupSuggestionAction.SuggestionsUpdated -> onSuggestionsUpdated(action.suggestions)
            is TabGroupSuggestionAction.SuggestionAccepted ->
                suggestionBeingActedOn?.let { suggestion -> telemetry.suggestionAccepted(suggestion) }
            is TabGroupSuggestionAction.SuggestionDismissed ->
                onSuggestionDismissed(action.suggestionId, suggestionBeingActedOn)
            is TabGroupSuggestionAction.OptOutToggled,
            TabGroupSuggestionAction.RefreshStarted,
            -> {
                // No side effects; these only affect in-memory state.
            }
        }
    }

    private fun onSuggestionsUpdated(suggestions: List<TabGroupSuggestion>) {
        suggestions.forEach { suggestion ->
            scope.launch {
                if (repository.lastShownAt(suggestion.id) == null) {
                    telemetry.suggestionShown(suggestion)
                }
                repository.recordShown(suggestion.id, System.currentTimeMillis())
            }
        }
    }

    private fun onSuggestionDismissed(suggestionId: String, suggestion: TabGroupSuggestion?) {
        suggestion?.let { telemetry.suggestionDismissed(it) }
        scope.launch { repository.dismiss(suggestionId) }
    }
}
