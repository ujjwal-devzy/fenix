/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.tabarchive

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Central entry point for the tab archive feature.
 *
 * Owns the storage handle and an in-memory index of archived tab ids so
 * callers on the hot path (e.g. the tabs tray adapter) can check archive
 * membership without touching disk.
 */
class TabArchiveManager private constructor(
    private val context: Context,
    private val storage: TabArchiveStorage,
) {

    private val archivedIndex = HashMap<String, Long>()

    /** Loads the archive index from disk. Called once at startup. */
    suspend fun warmUp() = withContext(Dispatchers.IO) {
        storage.getAll().forEach { entry ->
            archivedIndex[entry.tabId] = entry.archivedAt
        }
    }

    /** Whether the given tab id is currently archived. Safe to call from any thread. */
    fun isArchived(tabId: String): Boolean = archivedIndex.containsKey(tabId)

    /** Persists a batch of newly archived tabs and updates the index. */
    suspend fun archive(entries: List<TabArchiveEntry>) = withContext(Dispatchers.IO) {
        entries.forEach { entry ->
            storage.upsert(entry)
            archivedIndex[entry.tabId] = entry.archivedAt
        }
    }

    /** Restores a tab out of the archive. */
    suspend fun restore(tabId: String) = withContext(Dispatchers.IO) {
        storage.remove(tabId)
        archivedIndex.remove(tabId)
    }

    /** Drops entries past the retention window. Returns number of rows pruned. */
    suspend fun prune(retentionMs: Long): Int = withContext(Dispatchers.IO) {
        val cutoff = System.currentTimeMillis() - retentionMs
        val pruned = storage.pruneOlderThan(cutoff)
        archivedIndex.entries.removeAll { it.value < cutoff }
        pruned
    }

    companion object {
        @Volatile
        private var instance: TabArchiveManager? = null

        /**
         * Returns the process-wide manager, creating it on first use from the
         * caller's context.
         */
        fun getInstance(context: Context): TabArchiveManager {
            return instance ?: synchronized(this) {
                instance ?: TabArchiveManager(
                    context,
                    TabArchiveStorage(context),
                ).also { instance = it }
            }
        }
    }
}
