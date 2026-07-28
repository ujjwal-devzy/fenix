/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.home.tabgroupsuggestions

import mozilla.components.browser.state.store.BrowserStore
import org.mozilla.fenix.components.tabgroupsuggestions.TabGroupSuggestion
import org.mozilla.fenix.components.tabgroupsuggestions.store.TabGroupSuggestionState
import org.mozilla.fenix.components.tabgroupsuggestions.store.TabGroupSuggestionStatus
import org.mozilla.fenix.components.tabgroupsuggestions.store.TabGroupSuggestionStore
import org.mozilla.fenix.compose.tabgroupsuggestions.SuggestedTabPreview

/**
 * Adapts the tab group suggestions feature's own
 * [org.mozilla.fenix.components.tabgroupsuggestions.store.TabGroupSuggestionStore] and the app's
 * [BrowserStore] into the small, UI-ready shape the home screen section needs.
 *
 * Neither `org.mozilla.fenix.home.tabgroupsuggestions.TabGroupSuggestionsViewHolder` nor the
 * compose layer under `org.mozilla.fenix.compose.tabgroupsuggestions` needs to know how to
 * resolve a tab id into a title and url, or what the current [TabGroupSuggestionState] looks like
 * beyond "is there a suggestion to show right now" - that lookup logic lives here instead, so it
 * can be covered by its own unit tests independent of Compose.
 *
 * @property browserStore Used to resolve [TabGroupSuggestion.tabIds] into [SuggestedTabPreview]s.
 * @property tabGroupSuggestionStore Read to determine the current top suggestion and refresh
 * status.
 */
class TabGroupSuggestionsSectionUseCase(
    private val browserStore: BrowserStore,
    private val tabGroupSuggestionStore: TabGroupSuggestionStore,
) {

    /**
     * The highest ranked suggestion currently available, or `null` if there isn't one.
     */
    fun topSuggestion(): TabGroupSuggestion? = tabGroupSuggestionStore.state.suggestions.firstOrNull()

    /**
     * Whether a suggestion refresh triggered by a change in the user's open tabs is currently in
     * progress.
     */
    fun isRefreshing(): Boolean = tabGroupSuggestionStore.state.status == TabGroupSuggestionStatus.Refreshing

    /**
     * Resolves [suggestion]'s [TabGroupSuggestion.tabIds] into [SuggestedTabPreview]s, in the same
     * order, silently dropping any tab id that no longer refers to an open tab (e.g. because the
     * user closed it between the suggestion being computed and being rendered).
     */
    fun previewsFor(suggestion: TabGroupSuggestion): List<SuggestedTabPreview> {
        val tabsById = browserStore.state.tabs.associateBy { tab -> tab.id }

        return suggestion.tabIds.mapNotNull { tabId ->
            tabsById[tabId]?.let { tab ->
                SuggestedTabPreview(
                    tabId = tab.id,
                    title = tab.content.title,
                    url = tab.content.url,
                )
            }
        }
    }

    /**
     * The tab ids of [suggestion] that still correspond to an open tab, suitable for passing to
     * the collection creation flow when the user accepts the suggestion.
     */
    fun acceptableTabIds(suggestion: TabGroupSuggestion): Array<String> {
        val openTabIds = browserStore.state.tabs.map { tab -> tab.id }.toSet()
        return suggestion.tabIds.filter { tabId -> tabId in openTabIds }.toTypedArray()
    }
}
