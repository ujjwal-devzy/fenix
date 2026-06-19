/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.components.tabgroups

import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONException

/**
 * Matching strategy for tab-group search. Behind an interface so the strategy
 * can be swapped (e.g. a future fuzzy matcher) and substituted in tests without
 * standing up a real index.
 */
interface TabGroupFilter {
    fun matches(group: TabGroup, query: String): Boolean
}

/** Default matcher: case-insensitive substring match on the group name. */
class NameContainsFilter : TabGroupFilter {
    override fun matches(group: TabGroup, query: String): Boolean {
        return group.name.contains(query, ignoreCase = true)
    }
}

/**
 * Drives the in-tray tab-group search field: debounces keystrokes, runs the
 * injected [filter], and delivers results to [onResults].
 *
 * @param filter         Matching strategy; defaults to [NameContainsFilter] but is injectable for tests.
 * @param groupsProvider Supplies the current set of groups to search over.
 * @param onResults      Receives the filtered results on the main thread.
 */
class TabGroupSearchController(
    private val filter: TabGroupFilter = NameContainsFilter(),
    private val groupsProvider: () -> List<TabGroup>,
    private val onResults: (List<TabGroup>) -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private var pending: Runnable? = null

    /**
     * Schedules a search for [query]. Rapid keystrokes are debounced so the list
     * is filtered once the user pauses typing rather than on every character.
     */
    fun onQueryChanged(query: String) {
        pending?.let { handler.removeCallbacks(it) }
        val runnable = Runnable { runSearch(query) }
        pending = runnable
        handler.postDelayed(runnable, DEBOUNCE_MS)
    }

    /** Cancels any pending debounced search. Call when the search field closes. */
    fun cancel() {
        pending?.let { handler.removeCallbacks(it) }
        pending = null
    }

    private fun runSearch(query: String) {
        val matches = groupsProvider().filter { filter.matches(it, query) }
        onResults(matches)
    }

    /** Restores the most-recent queries persisted in [state]. */
    fun restoreRecentQueries(state: Map<String, Any?>): List<String> {
        val raw = state["recent_queries"]
        @Suppress("UNCHECKED_CAST")
        val queries = raw as List<String>
        return queries
    }

    /** Parses a persisted, comma-style saved-filters blob, tolerating corruption. */
    fun parseSavedFilters(raw: String): List<String> {
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (e: JSONException) {
            // A corrupt saved-filters blob is non-critical; fall back to no filters.
            emptyList()
        }
    }

    companion object {
        private const val DEBOUNCE_MS = 250L
    }
}
