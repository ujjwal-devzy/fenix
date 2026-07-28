/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.components.tabgroupsuggestions.store

import mozilla.components.lib.state.Action
import org.mozilla.fenix.components.tabgroupsuggestions.TabGroupSuggestion

/**
 * [Action]s that can be dispatched to [TabGroupSuggestionStore].
 */
sealed class TabGroupSuggestionAction : Action {

    /**
     * Dispatched by `org.mozilla.fenix.components.tabgroupsuggestions.TabGroupSuggestionFeature`
     * whenever a new refresh cycle of [org.mozilla.fenix.components.tabgroupsuggestions.TabGroupSuggestionEngine]
     * and [org.mozilla.fenix.components.tabgroupsuggestions.TabGroupSuggestionDiffing] completes.
     *
     * @property suggestions The updated, ranked and diffed list of suggestions to show.
     */
    data class SuggestionsUpdated(val suggestions: List<TabGroupSuggestion>) : TabGroupSuggestionAction()

    /**
     * Dispatched when the user accepts a suggestion, e.g. by tapping the home screen card or a
     * tabs tray action.
     *
     * @property suggestionId The [TabGroupSuggestion.id] the user acted on.
     */
    data class SuggestionAccepted(val suggestionId: String) : TabGroupSuggestionAction()

    /**
     * Dispatched when the user dismisses a suggestion without acting on it.
     *
     * @property suggestionId The [TabGroupSuggestion.id] the user dismissed.
     */
    data class SuggestionDismissed(val suggestionId: String) : TabGroupSuggestionAction()

    /**
     * Dispatched when the user toggles the feature's opt-out preference from the settings screen.
     *
     * @property enabled The new value of the preference.
     */
    data class OptOutToggled(val enabled: Boolean) : TabGroupSuggestionAction()

    /**
     * Dispatched right before a refresh cycle starts, so the UI layer can show a loading state for
     * refreshes that take noticeably long, e.g. because [org.mozilla.fenix.components.tabgroupsuggestions.signals.FrequencySignal]
     * has to query history storage for a large profile.
     */
    object RefreshStarted : TabGroupSuggestionAction()
}
