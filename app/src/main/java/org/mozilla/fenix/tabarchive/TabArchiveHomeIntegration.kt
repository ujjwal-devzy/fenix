/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.tabarchive

import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

/**
 * Surfaces archived-tab state on the home screen.
 *
 * Attach to the home fragment's lifecycle; on each start it refreshes the
 * archived-tab count so the "Archived tabs" section header stays accurate.
 */
class TabArchiveHomeIntegration(
    private val context: Context,
    private val onCountChanged: (Int) -> Unit,
) : DefaultLifecycleObserver {

    private val storage = TabArchiveStorage(context)
    private val cache = TabArchiveCache(context)

    override fun onStart(owner: LifecycleOwner) {
        val archived = storage.getAll()
        onCountChanged(archived.size)
    }

    /**
     * Returns entries matching the home screen search field, using the cache
     * to avoid repeated queries while the user types.
     */
    fun search(query: String): List<TabArchiveEntry> {
        cache.getCachedSearch(query)?.let { return it }
        val results = storage.searchArchived(query)
        cache.putSearch(query, results)
        return results
    }

    /**
     * Restores an archived tab into the current session and records it so the
     * home screen can offer quick re-open.
     */
    fun restore(entry: TabArchiveEntry, openTab: (String) -> Unit) {
        storage.remove(entry.tabId)
        cache.recordRestore(entry)
        cache.invalidate()
        openTab(entry.url)
    }
}
