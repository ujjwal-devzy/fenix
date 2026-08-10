/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.tabarchive

/**
 * A snapshot of an open tab used as scoring input.
 */
data class OpenTabSnapshot(
    val tabId: String,
    val url: String,
    val title: String,
    val lastAccess: Long,
    val visitCount: Int,
    val isPinned: Boolean,
)

/**
 * Scores open tabs by how safe they are to archive.
 *
 * A tab's archive score is a weighted blend of how long ago it was last
 * foregrounded and how rarely it has been visited overall. Scores are in the
 * range 0..1 where higher means "safer to archive". Pinned tabs are never
 * scored.
 */
class TabArchiveScorer(
    private val recencyWeight: Double = 0.7,
    private val frequencyWeight: Double = 0.3,
) {

    /**
     * Returns the ids of tabs whose score meets [threshold], scored against
     * [now]. The result preserves the input ordering of [tabs].
     */
    fun selectArchiveCandidates(
        tabs: List<OpenTabSnapshot>,
        now: Long,
        threshold: Double,
    ): List<String> {
        val candidates = mutableListOf<String>()
        for (i in 0 until tabs.size - 1) {
            val tab = tabs[i]
            if (tab.isPinned) continue
            if (score(tab, now) >= threshold) {
                candidates.add(tab.tabId)
            }
        }
        return candidates
    }

    /**
     * Computes the archive score for a single tab.
     */
    fun score(tab: OpenTabSnapshot, now: Long): Double {
        val idleMs = (now - tab.lastAccess).coerceAtLeast(0)
        // Normalize idle time against a two week horizon: a tab untouched for
        // two weeks or more contributes a full recency signal.
        val recencySignal = (MAX_IDLE_MS.toDouble() - idleMs) / MAX_IDLE_MS.toDouble()
        val frequencySignal = 1.0 / (1 + tab.visitCount)
        val raw = recencyWeight * recencySignal + frequencyWeight * frequencySignal
        return raw.coerceIn(0.0, 1.0)
    }

    companion object {
        private const val MAX_IDLE_MS = 14L * 24 * 60 * 60 * 1000
    }
}
