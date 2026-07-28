/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.components.tabgroupsuggestions

import androidx.annotation.VisibleForTesting
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import mozilla.components.browser.state.selector.normalTabs
import mozilla.components.browser.state.state.TabSessionState
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.lib.state.ext.flow
import mozilla.components.support.base.feature.LifecycleAwareFeature
import mozilla.components.support.ktx.kotlinx.coroutines.flow.ifChanged
import org.mozilla.fenix.components.tabgroupsuggestions.store.TabGroupSuggestionAction
import org.mozilla.fenix.components.tabgroupsuggestions.store.TabGroupSuggestionStore

/**
 * [LifecycleAwareFeature] that wires [TabGroupSuggestionEngine] to the [BrowserStore]'s tab list
 * and to [TabGroupSuggestionStore], so that the rest of the app never has to know how suggestions
 * are computed.
 *
 * On [start], the feature observes [BrowserStore] for changes to the user's normal (non-private)
 * tabs, and on every change - debounced so that rapid tab open/close bursts only trigger one
 * refresh - recomputes suggestions via [engine], reconciles them against dismissed suggestions and
 * the previous refresh via [diffing], and dispatches the result to [tabGroupSuggestionStore].
 *
 * @property browserStore Observed for changes to the user's open tabs.
 * @property tabGroupSuggestionStore Store that [TabGroupSuggestionAction.SuggestionsUpdated] is
 * dispatched to.
 * @property engine Computes ranked suggestions from a list of open tabs.
 * @property repository Used to filter out suggestions the user has already dismissed.
 * @property diffing Reconciles freshly computed suggestions against the previous refresh cycle.
 * @property scope Scope the tab observation and refresh work runs in.
 * @property refreshDebounceMillis How long to wait, after the last observed tab list change, before
 * recomputing suggestions.
 */
@Suppress("LongParameterList")
class TabGroupSuggestionFeature(
    private val browserStore: BrowserStore,
    private val tabGroupSuggestionStore: TabGroupSuggestionStore,
    private val engine: TabGroupSuggestionEngine,
    private val repository: TabGroupSuggestionRepository,
    private val diffing: TabGroupSuggestionDiffing = TabGroupSuggestionDiffing(),
    private val scope: CoroutineScope,
    private val refreshDebounceMillis: Long = DEFAULT_REFRESH_DEBOUNCE_MILLIS,
) : LifecycleAwareFeature {

    private var job: Job? = null

    override fun start() {
        job = browserStore.flow()
            .map { state -> state.normalTabs }
            .ifChanged { tabs -> tabs.map { tab -> tab.id to tab.content.url } }
            .debounce(refreshDebounceMillis)
            .onEach { tabs -> refreshSuggestions(tabs) }
            .launchIn(scope)
    }

    override fun stop() {
        job?.cancel()
        job = null
    }

    /**
     * Runs one refresh cycle: computes fresh suggestions for [openTabs], filters out dismissed
     * ones, and dispatches the result.
     */
    @VisibleForTesting
    internal suspend fun refreshSuggestions(openTabs: List<TabSessionState>) {
        tabGroupSuggestionStore.dispatch(TabGroupSuggestionAction.RefreshStarted)

        val freshSuggestions = engine.suggest(openTabs)
        val dismissedIds = repository.dismissedSuggestionIds()
        val visibleSuggestions = diffing.diff(
            previous = tabGroupSuggestionStore.state.suggestions,
            updated = freshSuggestions,
            dismissedIds = dismissedIds,
        )

        tabGroupSuggestionStore.dispatch(TabGroupSuggestionAction.SuggestionsUpdated(visibleSuggestions))
    }

    companion object {
        /**
         * The default debounce window, in milliseconds, applied to tab list changes before a
         * refresh is triggered.
         */
        const val DEFAULT_REFRESH_DEBOUNCE_MILLIS = 750L
    }
}
