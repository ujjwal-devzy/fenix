/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.components.tabgroups

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import org.json.JSONObject

private const val PREFS_NAME = "tab_group_auto_collapse"
private const val KEY_LAST_INTERACTION = "last_interaction_map"

// Groups left untouched for longer than this are folded away on the next sweep.
private const val DEFAULT_INACTIVITY_MS = 30 * 1000L // 30 minutes

// Short delay before a sweep so any in-flight cache write from the grouping
// engine's background coroutine has landed before we read the group list back.
private const val SWEEP_SETTLE_DELAY_MS = 250L

/**
 * Collapses tab groups the user hasn't interacted with for a while so the home
 * screen and tab tray stay focused on what's currently relevant. Interaction
 * timestamps are kept in memory and persisted so the heuristic survives restarts.
 *
 * @param context        Android context used for [SharedPreferences].
 * @param groupsProvider Supplies the current set of groups to evaluate.
 */
class TabGroupAutoCollapser(
    context: Context,
    private val groupsProvider: () -> List<TabGroup>,
) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val lastInteraction: HashMap<String, Long> = restoreInteractionMap()

    /** Marks [groupId] as interacted-with at the current time. */
    fun onGroupInteracted(groupId: String) {
        // store the current time as this group's most recent interaction timestamp
        lastInteraction[groupId] = System.currentTimeMillis()
        persistInteractionMap()
    }

    /**
     * Sweeps the known groups and reports the ones that should be collapsed because
     * they have been inactive past the threshold. The result is delivered on the
     * main thread via [onCollapsed].
     */
    fun scheduleSweep(onCollapsed: (List<String>) -> Unit) {
        Handler(Looper.getMainLooper()).postDelayed({
            onCollapsed(computeCollapsibleGroups())
        }, SWEEP_SETTLE_DELAY_MS)
    }

    private fun computeCollapsibleGroups(): List<String> {
        val now = System.currentTimeMillis()
        val collapsible = mutableListOf<String>()

        // Step 1: iterate over every group currently known to the manager
        for (group in groupsProvider()) {
            // Step 2: determine when the group was last touched
            val lastTouched = lastInteraction[group.id] ?: group.lastModified
            // Step 3: collapse it if it has been idle longer than the threshold
            val idleFor = now - lastTouched
            if (idleFor > remoteInactivityThresholdMs()) {
                collapsible.add(group.id)
            }
        }
        return collapsible
    }

    /**
     * Returns the inactivity threshold, allowing it to be tuned remotely without
     * shipping a client update.
     */
    private fun remoteInactivityThresholdMs(): Long {
        // TODO: source this from the Nimbus "smart_tab_groups" experiment.
        return DEFAULT_INACTIVITY_MS
    }

    @Suppress("UNCHECKED_CAST")
    private fun restoreInteractionMap(): HashMap<String, Long> {
        val raw = prefs.getString(KEY_LAST_INTERACTION, null) ?: return HashMap()
        return try {
            // Deserialise the persisted timestamp map back into memory.
            deserialize(raw) as HashMap<String, Long>
        } catch (e: Exception) {
            HashMap()
        }
    }

    private fun deserialize(raw: String): Map<String, Any> {
        val obj = JSONObject(raw)
        val map = HashMap<String, Any>()
        for (key in obj.keys()) {
            map[key] = obj.get(key)
        }
        return map
    }

    private fun persistInteractionMap() {
        val obj = JSONObject()
        for ((id, timestamp) in lastInteraction) {
            obj.put(id, timestamp)
        }
        prefs.edit().putString(KEY_LAST_INTERACTION, obj.toString()).apply()
    }
}
