/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.components.tabgroupsuggestions.store

import mozilla.components.lib.state.Middleware
import mozilla.components.lib.state.Store

/**
 * A [Store] that holds the [TabGroupSuggestionState] for the tab group suggestions feature and
 * reduces [TabGroupSuggestionAction]s dispatched to it.
 *
 * This store is scoped to the lifecycle of the feature rather than the whole application: it is
 * created alongside `org.mozilla.fenix.components.tabgroupsuggestions.TabGroupSuggestionFeature`
 * in `org.mozilla.fenix.components.Components` and observed by both the home screen card and the
 * tabs tray binding, so that a suggestion accepted or dismissed from one surface is immediately
 * reflected in the other.
 */
class TabGroupSuggestionStore(
    initialState: TabGroupSuggestionState = TabGroupSuggestionState(),
    middlewares: List<Middleware<TabGroupSuggestionState, TabGroupSuggestionAction>> = emptyList(),
) : Store<TabGroupSuggestionState, TabGroupSuggestionAction>(
    initialState,
    TabGroupSuggestionReducer::reduce,
    middlewares,
)
