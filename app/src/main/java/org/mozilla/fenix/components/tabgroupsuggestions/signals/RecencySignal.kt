/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.components.tabgroupsuggestions.signals

import org.mozilla.fenix.components.tabgroupsuggestions.CandidateGroupSignal
import org.mozilla.fenix.components.tabgroupsuggestions.CandidateTabGroup
import org.mozilla.fenix.components.tabgroupsuggestions.SuggestionSignal
import java.util.concurrent.TimeUnit

/**
 * A [CandidateGroupSignal] that scores a candidate group of tabs based on how recently, and how
 * closely together in time, its tabs were last accessed.
 *
 * Tabs that were all opened or revisited within the same short session are more likely to belong
 * to the same task the user is working on right now, e.g. comparing several product pages while
 * shopping, even if the other signals don't fire for them.
 *
 * @property recentWindowMillis The window, in milliseconds, within which the *average* age of the
 * candidate's tabs must fall for this signal to consider them recent. Candidates whose average age
 * exceeds the window are not considered stale by other signals, they simply don't get a recency
 * boost.
 */
class RecencySignal(
    private val recentWindowMillis: Long = TimeUnit.HOURS.toMillis(RECENT_WINDOW_HOURS),
) : CandidateGroupSignal {

    override suspend fun evaluate(candidate: CandidateTabGroup): SuggestionSignal? {
        if (candidate.tabs.isEmpty()) {
            return null
        }

        val ages = candidate.tabs.map { tab -> (candidate.now - tab.lastAccess).coerceAtLeast(0L) }
        val averageAge = ages.average().toLong()

        if (averageAge > recentWindowMillis) {
            return null
        }

        val normalizedAge = averageAge.toFloat() / recentWindowMillis.toFloat()
        val score = (1f - normalizedAge).coerceIn(SuggestionSignal.MIN_SCORE, SuggestionSignal.MAX_SCORE)

        return SuggestionSignal.Recency(
            score = score,
            rationale = "Tabs were accessed ${TimeUnit.MILLISECONDS.toMinutes(averageAge)} minutes ago on average",
            averageAgeMillis = averageAge,
        )
    }

    companion object {
        /**
         * The default window, in hours, used to decide whether a candidate's tabs were accessed
         * recently enough to boost its score.
         */
        const val RECENT_WINDOW_HOURS = 6L
    }
}
