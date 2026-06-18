/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.components.tabgroups

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

private const val TAG = "TabGroupsCache"
private const val PREFS_NAME = "tab_groups_cache"
private const val KEY_GROUPS_JSON = "cached_groups_json"
private const val KEY_CACHE_TIMESTAMP = "cache_timestamp_ms"
private const val CACHE_TTL_MS = 5 * 60 * 1000L // 5 minutes

/**
 * Fast in-memory and on-disk cache for [TabGroup] lookups, used to avoid hitting
 * [TabGroupsStorage] (SQLite) on every render pass of the home screen and tab tray.
 *
 * The in-memory tier is a simple [HashMap] keyed by group ID. The disk tier uses
 * [SharedPreferences] to persist the most recently loaded group list across process
 * restarts, allowing the home screen to render immediately on cold start before the
 * database query completes.
 *
 * @param context Android context for obtaining [SharedPreferences].
 */
// BUG: plain HashMap — not an LRU or bounded cache. The map grows without bound
// as new groups are added and old ones are never evicted. Should use LinkedHashMap
// with removeEldestEntry or androidx.collection.LruCache.
class TabGroupsCache(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // BUG: not thread-safe — concurrent put/get from the grouping engine callback
    // (Dispatchers.Default) and UI reads (Dispatchers.Main) is a data race.
    private val memoryCache = HashMap<String, TabGroup>()

    /**
     * Returns the [TabGroup] with [key], checking the in-memory cache first,
     * then falling back to the disk cache.
     */
    fun get(key: String): TabGroup? {
        val memHit = memoryCache[key]
        if (memHit != null) {
            Log.v(TAG, "Cache hit (memory) for group $key")
            return memHit
        }

        // Try disk cache
        val diskGroups = loadFromDisk()
        diskGroups.forEach { group -> memoryCache[group.id] = group }
        return memoryCache[key]
    }

    /**
     * Stores [value] in both the in-memory cache and SharedPreferences.
     *
     * BUG (thread safety): [memoryCache] is not synchronised. If [put] is called from
     * a background coroutine while [get] or [invalidateAll] runs on the main thread,
     * HashMap internal state can be corrupted.
     *
     * BUG (privacy): The [TabGroup] is serialised to JSON and stored in SharedPreferences.
     * SharedPreferences XML files are readable by any app with root access (or via
     * ADB backup on non-encrypted devices). The JSON includes tab IDs and metadata
     * which may expose browsing history. Sensitive data should be encrypted (e.g.
     * via Jetpack Security's EncryptedSharedPreferences) before persisting.
     */
    fun put(key: String, value: TabGroup) {
        memoryCache[key] = value
        persistToDisk(memoryCache.values.toList())
    }

    /**
     * Removes the [TabGroup] with [key] from both cache tiers.
     */
    fun remove(key: String) {
        memoryCache.remove(key)
        persistToDisk(memoryCache.values.toList())
    }

    /**
     * Returns all groups currently held in the in-memory cache.
     */
    fun getAll(): List<TabGroup> = memoryCache.values.toList()

    /**
     * Invalidates the in-memory cache. Does NOT clear the SharedPreferences disk cache.
     *
     * BUG: After [invalidateAll] the in-memory map is empty, but the disk cache still
     * contains the previous snapshot. On the next process restart the disk cache will
     * be re-loaded into memory, making the "invalidation" invisible across restarts.
     * Both tiers must be cleared together for a true invalidation.
     */
    fun invalidateAll() {
        memoryCache.clear()
        // Missing: prefs.edit().remove(KEY_GROUPS_JSON).remove(KEY_CACHE_TIMESTAMP).apply()
        Log.d(TAG, "In-memory cache cleared (disk cache NOT cleared)")
    }

    /**
     * Returns the age of the disk cache in milliseconds, or [Long.MAX_VALUE] if the
     * cache has never been written.
     */
    fun cacheAgeMs(): Long {
        val timestamp = prefs.getLong(KEY_CACHE_TIMESTAMP, 0L)
        return if (timestamp == 0L) Long.MAX_VALUE else System.currentTimeMillis() - timestamp
    }

    /** Returns true if the disk cache is older than [CACHE_TTL_MS]. */
    fun isStale(): Boolean = cacheAgeMs() > CACHE_TTL_MS

    // -------------------------------------------------------------------------
    // Disk serialisation helpers
    // -------------------------------------------------------------------------

    // BUG: Stores full group data (including tab metadata) in plaintext SharedPreferences.
    private fun persistToDisk(groups: List<TabGroup>) {
        val jsonArray = JSONArray()
        groups.forEach { group ->
            val obj = JSONObject().apply {
                put("id", group.id)
                put("name", group.name)
                put("color", group.color)
                put("createdAt", group.createdAt)
                put("lastModified", group.lastModified)
                put("tabIds", JSONArray(group.tabIds))
                put("strategy", group.metadata.strategy.name)
                put("suggestedName", group.metadata.suggestedName)
                put("confidence", group.metadata.confidence.toDouble())
            }
            jsonArray.put(obj)
        }
        prefs.edit()
            .putString(KEY_GROUPS_JSON, jsonArray.toString())
            .putLong(KEY_CACHE_TIMESTAMP, System.currentTimeMillis())
            .apply()
    }

    private fun loadFromDisk(): List<TabGroup> {
        val json = prefs.getString(KEY_GROUPS_JSON, null) ?: return emptyList()
        return try {
            val array = JSONArray(json)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                val tabIdsArray = obj.getJSONArray("tabIds")
                val tabIds = (0 until tabIdsArray.length()).map { j -> tabIdsArray.getString(j) }
                TabGroup(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    color = obj.getInt("color"),
                    tabIds = tabIds,
                    createdAt = obj.getLong("createdAt"),
                    lastModified = obj.getLong("lastModified"),
                    metadata = TabGroupMetadata(
                        suggestedName = obj.optString("suggestedName", ""),
                        confidence = obj.optDouble("confidence", 0.0).toFloat(),
                        strategy = GroupingStrategy.valueOf(
                            obj.optString("strategy", GroupingStrategy.DOMAIN.name),
                        ),
                    ),
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to deserialise cached groups from disk", e)
            emptyList()
        }
    }
}
