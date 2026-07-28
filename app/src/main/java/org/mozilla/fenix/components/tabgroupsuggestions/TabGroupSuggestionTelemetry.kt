/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.components.tabgroupsuggestions

import org.mozilla.fenix.GleanMetrics.TabGroupSuggestions

/**
 * Thin wrapper around the `tab_group_suggestions` Glean metrics defined in `metrics.yaml`.
 *
 * Kept as its own class, rather than calling [TabGroupSuggestions] directly from
 * `org.mozilla.fenix.components.tabgroupsuggestions.store.TabGroupSuggestionMiddleware`, so that
 * telemetry recording can be verified or faked in tests without depending on Glean's own test
 * APIs.
 */
class TabGroupSuggestionTelemetry {

    /**
     * Records that [suggestion] was shown to the user.
     */
    fun suggestionShown(suggestion: TabGroupSuggestion) {
        TabGroupSuggestions.shown.record(
            TabGroupSuggestions.ShownExtra(
                suggestionId = suggestion.id,
                tabCount = suggestion.tabCount,
            ),
        )
    }

    /**
     * Records that the user accepted [suggestion].
     */
    fun suggestionAccepted(suggestion: TabGroupSuggestion) {
        TabGroupSuggestions.accepted.record(
            TabGroupSuggestions.AcceptedExtra(
                suggestionId = suggestion.id,
                tabCount = suggestion.tabCount,
            ),
        )
    }

    /**
     * Records that the user dismissed [suggestion] without acting on it.
     */
    fun suggestionDismissed(suggestion: TabGroupSuggestion) {
        TabGroupSuggestions.dismissed.record(
            TabGroupSuggestions.DismissedExtra(
                suggestionId = suggestion.id,
                tabCount = suggestion.tabCount,
            ),
        )
    }

    /**
     * Records whether the tab group suggestions card is currently visible on the home screen.
     */
    fun sectionVisibilityChanged(visible: Boolean) {
        TabGroupSuggestions.sectionVisible.set(visible)
    }
}
