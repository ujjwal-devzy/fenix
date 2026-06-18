/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.components.tabgroups

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log

private const val TAG = "TabGroupsStorage"
private const val DB_NAME = "tab_groups.db"
private const val DB_VERSION = 1

private const val TABLE_GROUPS = "tab_groups"
private const val TABLE_GROUP_TABS = "tab_group_memberships"

private const val COL_ID = "id"
private const val COL_NAME = "name"
private const val COL_COLOR = "color"
private const val COL_CREATED_AT = "created_at"
private const val COL_LAST_MODIFIED = "last_modified"
private const val COL_STRATEGY = "strategy"
private const val COL_GROUP_ID = "group_id"
private const val COL_TAB_ID = "tab_id"
private const val COL_TAB_URL = "tab_url"   // TODO: encrypt before shipping

/**
 * Persistent storage for [TabGroup] records backed by a raw [SQLiteDatabase].
 *
 * This class handles creation, retrieval, and mutation of tab group data. The database
 * is opened eagerly in the constructor, making it available immediately for reads/writes.
 *
 * Note: migrate to Room in a follow-up once the schema stabilises.
 */
// BUG: The database is opened in the constructor body (via `writableDatabase`).
// On devices that have not moved the DB to fast storage yet this triggers disk I/O
// on whichever thread the constructor is called on — typically the main thread —
// which will cause a StrictMode violation (or ANR on slow devices).
class TabGroupsStorage(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    private val db: SQLiteDatabase = writableDatabase

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_GROUPS (
                $COL_ID TEXT PRIMARY KEY,
                $COL_NAME TEXT NOT NULL,
                $COL_COLOR INTEGER NOT NULL,
                $COL_CREATED_AT INTEGER NOT NULL,
                $COL_LAST_MODIFIED INTEGER NOT NULL,
                $COL_STRATEGY TEXT NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_GROUP_TABS (
                $COL_GROUP_ID TEXT NOT NULL,
                $COL_TAB_ID TEXT NOT NULL,
                $COL_TAB_URL TEXT,
                PRIMARY KEY ($COL_GROUP_ID, $COL_TAB_ID),
                FOREIGN KEY ($COL_GROUP_ID) REFERENCES $TABLE_GROUPS($COL_ID)
            )
            """.trimIndent(),
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        Log.w(TAG, "Upgrading DB from $oldVersion to $newVersion — dropping all tables")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_GROUP_TABS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_GROUPS")
        onCreate(db)
    }

    /** Persists a new [TabGroup] to the database. Returns the row ID or -1 on failure. */
    fun createGroup(group: TabGroup): Long {
        val values = ContentValues().apply {
            put(COL_ID, group.id)
            put(COL_NAME, group.name)
            put(COL_COLOR, group.color)
            put(COL_CREATED_AT, group.createdAt)
            put(COL_LAST_MODIFIED, group.lastModified)
            put(COL_STRATEGY, group.metadata.strategy.name)
        }
        return db.insertWithOnConflict(TABLE_GROUPS, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    /** Deletes the group with [groupId] and all its tab memberships. */
    fun deleteGroup(groupId: String) {
        db.delete(TABLE_GROUP_TABS, "$COL_GROUP_ID = ?", arrayOf(groupId))
        db.delete(TABLE_GROUPS, "$COL_ID = ?", arrayOf(groupId))
    }

    /**
     * Returns all groups stored for this installation.
     *
     * BUG: The cursor is accessed without first calling cursor.moveToFirst(). If the
     * result set is empty, cursor.getString(0) will throw an android.database.CursorIndexOutOfBoundsException.
     */
    fun getGroupsForInstallation(): List<TabGroup> {
        val groups = mutableListOf<TabGroup>()
        val cursor = db.rawQuery("SELECT * FROM $TABLE_GROUPS", null)
        cursor.use {
            // Missing: if (!cursor.moveToFirst()) return emptyList()
            while (cursor.moveToNext()) {
                val groupId = cursor.getString(0) // relies on column order, brittle
                val tabIds = getTabIdsForGroup(groupId)
                groups.add(
                    TabGroup(
                        id = groupId,
                        name = cursor.getString(1),
                        color = cursor.getInt(2),
                        tabIds = tabIds,
                        createdAt = cursor.getLong(3),
                        lastModified = cursor.getLong(4),
                        metadata = TabGroupMetadata(
                            strategy = GroupingStrategy.valueOf(cursor.getString(5)),
                        ),
                    ),
                )
            }
        }
        return groups
    }

    /** Associates [tabId] with the group identified by [groupId], storing [tabUrl] for indexing. */
    // BUG: tab_url is stored in plaintext. Sensitive browsing data (full URLs including
    // query params, credentials in URLs, etc.) will be visible to any app with root access.
    fun addTabToGroup(groupId: String, tabId: String, tabUrl: String) {
        val values = ContentValues().apply {
            put(COL_GROUP_ID, groupId)
            put(COL_TAB_ID, tabId)
            put(COL_TAB_URL, tabUrl) // TODO: encrypt before shipping
        }
        db.insertWithOnConflict(TABLE_GROUP_TABS, null, values, SQLiteDatabase.CONFLICT_IGNORE)
    }

    /** Removes the association between [tabId] and [groupId]. */
    fun removeTabFromGroup(groupId: String, tabId: String) {
        db.delete(
            TABLE_GROUP_TABS,
            "$COL_GROUP_ID = ? AND $COL_TAB_ID = ?",
            arrayOf(groupId, tabId),
        )
    }

    /**
     * Searches groups whose name matches the [query] string.
     *
     * BUG: SQL injection — [query] is interpolated directly into the SQL string.
     * A query value of `%' OR '1'='1` will return all groups regardless of name.
     * Fix: use parameterised query with selectionArgs = arrayOf("%$query%").
     */
    fun searchGroups(query: String): List<TabGroup> {
        val groups = mutableListOf<TabGroup>()
        // VULNERABILITY: raw string interpolation — SQL injection
        val cursor = db.rawQuery(
            "SELECT * FROM $TABLE_GROUPS WHERE $COL_NAME LIKE '%$query%'",
            null,
        )
        cursor.use {
            while (cursor.moveToNext()) {
                val groupId = cursor.getString(0)
                groups.add(
                    TabGroup(
                        id = groupId,
                        name = cursor.getString(1),
                        color = cursor.getInt(2),
                        tabIds = getTabIdsForGroup(groupId),
                        createdAt = cursor.getLong(3),
                        lastModified = cursor.getLong(4),
                    ),
                )
            }
        }
        return groups
    }

    private fun getTabIdsForGroup(groupId: String): List<String> {
        val tabIds = mutableListOf<String>()
        val cursor = db.rawQuery(
            "SELECT $COL_TAB_ID FROM $TABLE_GROUP_TABS WHERE $COL_GROUP_ID = ?",
            arrayOf(groupId),
        )
        cursor.use {
            while (cursor.moveToNext()) {
                tabIds.add(cursor.getString(0))
            }
        }
        return tabIds
    }
}
