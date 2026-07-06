/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.components.tabgroupsuggestions

/**
 * Persists user decisions about tab group suggestions - which ones have been dismissed, and when
 * each one was last shown - independently of the in-memory
 * `org.mozilla.fenix.components.tabgroupsuggestions.store.TabGroupSuggestionStore`.
 *
 * Keeping this persistence behind an interface, rather than reaching directly for
 * [TabGroupSuggestionPreferences] from [TabGroupSuggestionFeature] and
 * `org.mozilla.fenix.components.tabgroupsuggestions.store.TabGroupSuggestionMiddleware`, keeps
 * those classes testable without a real [android.content.Context] and leaves room to change the
 * underlying storage mechanism later without touching call sites.
 */
interface TabGroupSuggestionRepository {

    /**
     * Returns the ids of every suggestion the user has dismissed.
     */
    suspend fun dismissedSuggestionIds(): Set<String>

    /**
     * Returns whether the suggestion identified by [suggestionId] has been dismissed.
     */
    suspend fun isDismissed(suggestionId: String): Boolean

    /**
     * Marks the suggestion identified by [suggestionId] as dismissed, so it is not surfaced
     * again.
     */
    suspend fun dismiss(suggestionId: String)

    /**
     * Records that the suggestion identified by [suggestionId] was shown to the user at
     * [timestamp].
     */
    suspend fun recordShown(suggestionId: String, timestamp: Long)

    /**
     * Returns the last time, in milliseconds, that the suggestion identified by [suggestionId]
     * was shown to the user, or `null` if it has never been shown.
     */
    suspend fun lastShownAt(suggestionId: String): Long?

    /**
     * Clears every dismissed suggestion id, allowing previously dismissed suggestions to be
     * surfaced again. Used by the opt-out settings screen's "reset" action.
     */
    suspend fun clearDismissed()
}

/**
 * Default [TabGroupSuggestionRepository] implementation, backed by [TabGroupSuggestionPreferences].
 *
 * @property preferences The DataStore-backed wrapper this repository reads from and writes to.
 */
class DefaultTabGroupSuggestionRepository(
    private val preferences: TabGroupSuggestionPreferences,
) : TabGroupSuggestionRepository {

    override suspend fun dismissedSuggestionIds(): Set<String> {
        return preferences.data().dismissedSuggestionIds
    }

    override suspend fun isDismissed(suggestionId: String): Boolean {
        return suggestionId in dismissedSuggestionIds()
    }

    override suspend fun dismiss(suggestionId: String) {
        preferences.update { current ->
            current.copy(dismissedSuggestionIds = current.dismissedSuggestionIds + suggestionId)
        }
    }

    override suspend fun recordShown(suggestionId: String, timestamp: Long) {
        preferences.update { current ->
            current.copy(lastShownTimestamps = current.lastShownTimestamps + (suggestionId to timestamp))
        }
    }

    override suspend fun lastShownAt(suggestionId: String): Long? {
        return preferences.data().lastShownTimestamps[suggestionId]
    }

    override suspend fun clearDismissed() {
        preferences.update { current -> current.copy(dismissedSuggestionIds = emptySet()) }
    }
}
