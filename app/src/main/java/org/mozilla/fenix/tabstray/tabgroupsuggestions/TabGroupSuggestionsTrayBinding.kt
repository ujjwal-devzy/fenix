/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.tabstray.tabgroupsuggestions

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import mozilla.components.lib.state.helpers.AbstractBinding
import mozilla.components.support.ktx.kotlinx.coroutines.flow.ifChanged
import org.mozilla.fenix.components.tabgroupsuggestions.TabGroupSuggestion
import org.mozilla.fenix.components.tabgroupsuggestions.store.TabGroupSuggestionAction
import org.mozilla.fenix.components.tabgroupsuggestions.store.TabGroupSuggestionState
import org.mozilla.fenix.components.tabgroupsuggestions.store.TabGroupSuggestionStore

/**
 * Binds the tabs tray to [tabGroupSuggestionStore], surfacing the top available
 * [TabGroupSuggestion] as a snackbar action so that users who are already in the tabs tray - a
 * natural place to be thinking about tab organization - don't have to go back to the home screen
 * to act on a suggestion.
 *
 * This mirrors how `org.mozilla.fenix.tabstray.CloseOnLastTabBinding` and its siblings observe a
 * store via [AbstractBinding], except the store being observed here is the tab group suggestions
 * feature's own [TabGroupSuggestionStore] rather than the shared `BrowserStore`.
 *
 * @property tabGroupSuggestionStore Store observed for suggestion updates.
 * @property onSuggestionAvailable Invoked with a suggestion when it should be surfaced in the
 * tray, e.g. via a snackbar. Left as a callback, rather than owning snackbar presentation
 * directly, so this class stays testable without a `View`.
 * @property onSuggestionAccepted Invoked when the user acts on the suggestion surfaced by
 * [onSuggestionAvailable].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TabGroupSuggestionsTrayBinding(
    private val tabGroupSuggestionStore: TabGroupSuggestionStore,
    private val onSuggestionAvailable: (TabGroupSuggestion) -> Unit,
    private val onSuggestionAccepted: (TabGroupSuggestion) -> Unit,
) : AbstractBinding<TabGroupSuggestionState>(tabGroupSuggestionStore) {

    override suspend fun onState(flow: Flow<TabGroupSuggestionState>) {
        flow
            .map { state -> state.suggestions.firstOrNull() }
            .ifChanged()
            .collect { suggestion ->
                if (suggestion != null) {
                    onSuggestionAvailable(suggestion)
                }
            }
    }

    /**
     * Called by the tabs tray fragment when the user taps the snackbar action surfaced via
     * [onSuggestionAvailable].
     */
    fun accept(suggestion: TabGroupSuggestion) {
        tabGroupSuggestionStore.dispatch(TabGroupSuggestionAction.SuggestionAccepted(suggestion.id))
        onSuggestionAccepted(suggestion)
    }
}
