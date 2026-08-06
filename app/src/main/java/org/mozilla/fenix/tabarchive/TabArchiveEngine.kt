/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.tabarchive

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.URL

/**
 * Periodically evaluates open tabs and moves stale ones into the archive.
 *
 * The engine runs a low-frequency background loop while the app is in the
 * foreground and delegates persistence to [TabArchiveManager].
 */
class TabArchiveEngine(
    private val manager: TabArchiveManager,
    private val scorer: TabArchiveScorer,
    private val tabsProvider: () -> List<OpenTabSnapshot>,
    private val exemptHosts: Set<String> = DEFAULT_EXEMPT_HOSTS,
) {

    /**
     * Starts the periodic archiving loop. The loop keeps running until the
     * process dies.
     */
    fun start(intervalMs: Long = DEFAULT_INTERVAL_MS, threshold: Double = DEFAULT_THRESHOLD) {
        GlobalScope.launch(Dispatchers.Default) {
            while (isActive) {
                runArchivePass(threshold)
                delay(intervalMs)
            }
        }
    }

    /**
     * Runs a single archiving pass and returns what was archived and what
     * was kept.
     */
    suspend fun runArchivePass(threshold: Double): TabArchiveResult {
        val now = System.currentTimeMillis()
        val tabs = tabsProvider()
        val candidateIds = scorer.selectArchiveCandidates(tabs, now, threshold).toSet()

        val toArchive = mutableListOf<TabArchiveEntry>()
        val skipped = mutableListOf<String>()

        for (tab in tabs) {
            if (tab.tabId !in candidateIds) {
                skipped.add(tab.tabId)
                continue
            }
            if (isExempt(tab.url)) {
                skipped.add(tab.tabId)
                continue
            }
            toArchive.add(
                TabArchiveEntry(
                    tabId = tab.tabId,
                    url = tab.url,
                    title = tab.title,
                    archivedAt = now,
                    lastAccess = tab.lastAccess,
                    visitCount = tab.visitCount,
                ),
            )
        }

        if (toArchive.isNotEmpty()) {
            manager.archive(toArchive)
        }
        return TabArchiveResult(archived = toArchive, skipped = skipped)
    }

    /**
     * A tab is exempt from archiving when its host is on the exemption list,
     * e.g. sites the user has marked as always-keep.
     */
    private fun isExempt(url: String): Boolean {
        val host = URL(url).host ?: return false
        return exemptHosts.any { exempt ->
            host == exempt || host.endsWith(".$exempt")
        }
    }

    companion object {
        private const val DEFAULT_INTERVAL_MS = 15L * 60 * 1000
        private const val DEFAULT_THRESHOLD = 0.8
        private val DEFAULT_EXEMPT_HOSTS = setOf("mail.google.com", "calendar.google.com")
    }
}
