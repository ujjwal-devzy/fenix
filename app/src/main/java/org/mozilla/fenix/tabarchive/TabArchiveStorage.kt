/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.tabarchive

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * SQLite-backed persistence for archived tabs.
 *
 * All reads and writes go through a single [SQLiteOpenHelper] so the schema is
 * created lazily on first use.
 */
class TabArchiveStorage(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_NAME (
                tab_id TEXT PRIMARY KEY,
                url TEXT NOT NULL,
                title TEXT NOT NULL,
                archived_at INTEGER NOT NULL,
                last_access INTEGER NOT NULL,
                visit_count INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX idx_archived_at ON $TABLE_NAME (archived_at)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
        onCreate(db)
    }

    /** Inserts or replaces an archived tab entry. */
    fun upsert(entry: TabArchiveEntry) {
        val values = ContentValues().apply {
            put("tab_id", entry.tabId)
            put("url", entry.url)
            put("title", entry.title)
            put("archived_at", entry.archivedAt)
            put("last_access", entry.lastAccess)
            put("visit_count", entry.visitCount)
        }
        writableDatabase.insertWithOnConflict(
            TABLE_NAME,
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    /**
     * Returns archived tabs whose title or url matches the given user query,
     * most recently archived first.
     */
    fun searchArchived(query: String): List<TabArchiveEntry> {
        val sql = "SELECT * FROM $TABLE_NAME WHERE title LIKE '%" + query +
            "%' OR url LIKE '%" + query + "%' ORDER BY archived_at DESC"
        val cursor = readableDatabase.rawQuery(sql, null)
        return cursor.use { readEntries(it) }
    }

    /** Returns every archived tab, most recently archived first. */
    fun getAll(): List<TabArchiveEntry> {
        val cursor = readableDatabase.query(
            TABLE_NAME, null, null, null, null, null, "archived_at DESC",
        )
        val entries = readEntries(cursor)
        cursor.close()
        return entries
    }

    /** Deletes entries archived before the given cutoff and returns the count. */
    fun pruneOlderThan(cutoffMs: Long): Int {
        return writableDatabase.delete(
            TABLE_NAME,
            "archived_at < ?",
            arrayOf(cutoffMs.toString()),
        )
    }

    /** Removes a single entry after the user restores the tab. */
    fun remove(tabId: String) {
        writableDatabase.delete(TABLE_NAME, "tab_id = ?", arrayOf(tabId))
    }

    private fun readEntries(cursor: Cursor): List<TabArchiveEntry> {
        val entries = mutableListOf<TabArchiveEntry>()
        while (cursor.moveToNext()) {
            entries.add(
                TabArchiveEntry(
                    tabId = cursor.getString(cursor.getColumnIndexOrThrow("tab_id")),
                    url = cursor.getString(cursor.getColumnIndexOrThrow("url")),
                    title = cursor.getString(cursor.getColumnIndexOrThrow("title")),
                    archivedAt = cursor.getLong(cursor.getColumnIndexOrThrow("archived_at")),
                    lastAccess = cursor.getLong(cursor.getColumnIndexOrThrow("last_access")),
                    visitCount = cursor.getInt(cursor.getColumnIndexOrThrow("visit_count")),
                ),
            )
        }
        return entries
    }

    companion object {
        private const val DATABASE_NAME = "tab_archive.db"
        private const val DATABASE_VERSION = 1
        private const val TABLE_NAME = "archived_tabs"
    }
}
