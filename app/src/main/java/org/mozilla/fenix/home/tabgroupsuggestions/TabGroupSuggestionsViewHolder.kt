/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.home.tabgroupsuggestions

import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.LifecycleOwner
import mozilla.components.lib.state.ext.observeAsComposableState
import org.mozilla.fenix.components.tabgroupsuggestions.TabGroupSuggestion
import org.mozilla.fenix.components.tabgroupsuggestions.store.TabGroupSuggestionAction
import org.mozilla.fenix.components.tabgroupsuggestions.store.TabGroupSuggestionState
import org.mozilla.fenix.components.tabgroupsuggestions.store.TabGroupSuggestionStatus
import org.mozilla.fenix.components.tabgroupsuggestions.store.TabGroupSuggestionStore
import org.mozilla.fenix.compose.ComposeViewHolder
import org.mozilla.fenix.compose.tabgroupsuggestions.TabGroupSuggestionCard

/**
 * Contract for handling user interactions with the tab group suggestions home screen section.
 * Implemented by [DefaultTabGroupSuggestionsInteractor] and faked in tests.
 */
interface TabGroupSuggestionsInteractor {

    /**
     * Called when the user accepts [suggestion] from the home screen card.
     */
    fun onSuggestionAccepted(suggestion: TabGroupSuggestion)

    /**
     * Called when the user dismisses [suggestion] from the home screen card.
     */
    fun onSuggestionDismissed(suggestion: TabGroupSuggestion)
}

/**
 * Default [TabGroupSuggestionsInteractor], dispatching to [tabGroupSuggestionStore] and delegating
 * navigation to [navigateToCollectionCreation], which is supplied by `HomeFragment` since it is the
 * one with access to a `NavController`.
 *
 * @property tabGroupSuggestionStore Store dispatched to when the user acts on a suggestion.
 * @property navigateToCollectionCreation Invoked with an accepted suggestion so the host fragment
 * can open the existing "save to collection" flow pre-selecting its tabs.
 */
class DefaultTabGroupSuggestionsInteractor(
    private val tabGroupSuggestionStore: TabGroupSuggestionStore,
    private val navigateToCollectionCreation: (TabGroupSuggestion) -> Unit,
) : TabGroupSuggestionsInteractor {

    override fun onSuggestionAccepted(suggestion: TabGroupSuggestion) {
        tabGroupSuggestionStore.dispatch(TabGroupSuggestionAction.SuggestionAccepted(suggestion.id))
        navigateToCollectionCreation(suggestion)
    }

    override fun onSuggestionDismissed(suggestion: TabGroupSuggestion) {
        tabGroupSuggestionStore.dispatch(TabGroupSuggestionAction.SuggestionDismissed(suggestion.id))
    }
}

/**
 * [ComposeViewHolder] that renders [TabGroupSuggestionCard] as a home screen section, backed by
 * [tabGroupSuggestionStore] and [sectionUseCase].
 *
 * @property composeView The [ComposeView] this view holder will render into.
 * @property viewLifecycleOwner [LifecycleOwner] life-cycle owner for the view.
 * @property tabGroupSuggestionStore Store observed for the current suggestion and refresh status.
 * @property sectionUseCase Resolves the current suggestion into UI-ready tab previews.
 * @property interactor Handles user interactions with the rendered card.
 */
class TabGroupSuggestionsViewHolder(
    composeView: ComposeView,
    viewLifecycleOwner: LifecycleOwner,
    private val tabGroupSuggestionStore: TabGroupSuggestionStore,
    private val sectionUseCase: TabGroupSuggestionsSectionUseCase,
    private val interactor: TabGroupSuggestionsInteractor,
) : ComposeViewHolder(composeView, viewLifecycleOwner) {

    @Composable
    override fun Content() {
        val state = tabGroupSuggestionStore.observeAsComposableState { it }.value ?: TabGroupSuggestionState()
        val suggestion = state.suggestions.firstOrNull()
        val previews = suggestion?.let { sectionUseCase.previewsFor(it) }.orEmpty()

        TabGroupSuggestionCard(
            suggestion = suggestion,
            tabPreviews = previews,
            isLoading = state.status == TabGroupSuggestionStatus.Refreshing && suggestion == null,
            onSuggestionClick = { suggestion?.let(interactor::onSuggestionAccepted) },
            onDismiss = { suggestion?.let(interactor::onSuggestionDismissed) },
        )
    }

    companion object {
        val LAYOUT_ID = View.generateViewId()
    }
}
