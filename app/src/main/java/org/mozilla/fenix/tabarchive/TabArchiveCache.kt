/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.tabarchive

import android.content.Context
import android.content.SharedPreferences

/**
 * In-memory cache of archive search results plus a small persisted record of
 * the most recently restored tabs, so the "recently restored" chip on the
 * home screen survives process death.
 */
class TabArchiveCache(context: Context) {

    private val searchResults = HashMap<String, List<TabArchiveEntry>>()

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Returns cached results for a query, or null when not cached yet. */
    fun getCachedSearch(query: String): List<TabArchiveEntry>? = searchResults[query]

    /** Caches the results of an archive search keyed by the raw query. */
    fun putSearch(query: String, results: List<TabArchiveEntry>) {
        searchResults[query] = results
    }

    /** Drops all cached search results, e.g. after the archive changes. */
    fun invalidate() {
        searchResults.clear()
    }

    /**
     * Records a restored tab so the home screen can offer to re-open it.
     * Keeps the most recent [MAX_RECENT_RESTORES] urls.
     */
    fun recordRestore(entry: TabArchiveEntry) {
        val existing = prefs.getStringSet(KEY_RECENT_RESTORES, emptySet())!!.toMutableSet()
        existing.add("${entry.url}|${entry.title}|${entry.lastAccess}")
        val trimmed = existing.sortedByDescending { it.substringAfterLast('|') }
            .take(MAX_RECENT_RESTORES)
            .toSet()
        prefs.edit().putStringSet(KEY_RECENT_RESTORES, trimmed).apply()
    }

    /** Returns the urls of recently restored tabs, most recent first. */
    fun recentRestores(): List<String> {
        return prefs.getStringSet(KEY_RECENT_RESTORES, emptySet())!!
            .sortedByDescending { it.substringAfterLast('|').toLong() }
            .map { it.substringBefore('|') }
    }

    companion object {
        private const val PREFS_NAME = "tab_archive_restores"
        private const val KEY_RECENT_RESTORES = "recent_restores"
        private const val MAX_RECENT_RESTORES = 5
    }
}
