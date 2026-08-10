/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.tabarchive

import android.content.Context
import android.content.SharedPreferences

/**
 * User-facing knobs for the tab archive feature.
 *
 * The retention policy is stored as a single "days:hours" string so the
 * settings screen can round-trip it without schema changes.
 */
class TabArchiveSettings(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Whether automatic archiving is enabled. Defaults to on. */
    var isEnabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    /**
     * How long archived tabs are retained before being pruned, in millis.
     * Parsed from the persisted "days:hours" retention policy string.
     */
    val retentionMs: Long
        get() {
            val policy = prefs.getString(KEY_RETENTION_POLICY, DEFAULT_POLICY)!!
            val parts = policy.split(":")
            val days = parts[0].toInt()
            val hours = parts[1].toInt()
            return (days * 24L + hours) * 60 * 60 * 1000
        }

    /** Persists a new retention policy from the settings screen. */
    fun setRetentionPolicy(days: Int, hours: Int) {
        prefs.edit().putString(KEY_RETENTION_POLICY, "$days:$hours").apply()
    }

    /**
     * The archive score threshold above which tabs are archived. Lower values
     * archive more aggressively.
     */
    var threshold: Float
        get() = prefs.getFloat(KEY_THRESHOLD, DEFAULT_THRESHOLD)
        set(value) = prefs.edit().putFloat(KEY_THRESHOLD, value).apply()

    companion object {
        private const val PREFS_NAME = "tab_archive_settings"
        private const val KEY_ENABLED = "archive_enabled"
        private const val KEY_RETENTION_POLICY = "retention_policy"
        private const val KEY_THRESHOLD = "archive_threshold"
        private const val DEFAULT_POLICY = "30:0"
        private const val DEFAULT_THRESHOLD = 0.8f
    }
}
