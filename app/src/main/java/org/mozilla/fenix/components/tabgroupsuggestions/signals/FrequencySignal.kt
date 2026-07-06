/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.components.tabgroupsuggestions.signals

import mozilla.components.concept.storage.HistoryStorage
import org.mozilla.fenix.components.tabgroupsuggestions.CandidateGroupSignal
import org.mozilla.fenix.components.tabgroupsuggestions.CandidateTabGroup
import org.mozilla.fenix.components.tabgroupsuggestions.SuggestionSignal
import java.util.concurrent.TimeUnit

/**
 * A [CandidateGroupSignal] that scores a candidate group of tabs based on how often the user has
 * historically visited the pages they point to.
 *
 * Unlike [org.mozilla.fenix.components.tabgroupsuggestions.signals.RecencySignal], which looks at
 * when a tab was last touched, this signal looks at [HistoryStorage] to see whether the candidate
 * represents pages the user returns to often, which is a good indicator of an ongoing task or
 * interest rather than a one-off visit.
 *
 * @property historyStorage Storage used to look up recorded visits for each tab's url.
 * @property lookbackDays How many days of visit history to consider when computing visit counts.
 */
class FrequencySignal(
    private val historyStorage: HistoryStorage,
    private val lookbackDays: Long = DEFAULT_LOOKBACK_DAYS,
) : CandidateGroupSignal {

    override suspend fun evaluate(candidate: CandidateTabGroup): SuggestionSignal? {
        if (candidate.tabs.size < MIN_TABS_FOR_SIGNAL) {
            return null
        }

        val end = candidate.now
        val start = end - TimeUnit.DAYS.toMillis(lookbackDays)
        val visits = historyStorage.getDetailedVisits(start = start, end = end)
        if (visits.isEmpty()) {
            return null
        }

        val visitCountByUrl = visits.groupingBy { visit -> visit.url }.eachCount()
        val visitCounts = candidate.tabs.map { tab -> visitCountByUrl[tab.content.url] ?: 0 }
        val averageVisitCount = visitCounts.average()

        if (averageVisitCount < MIN_AVERAGE_VISITS) {
            return null
        }

        val score = (averageVisitCount / MAX_VISITS_FOR_FULL_SCORE)
            .coerceIn(SuggestionSignal.MIN_SCORE.toDouble(), SuggestionSignal.MAX_SCORE.toDouble())
            .toFloat()

        return SuggestionSignal.Frequency(
            score = score,
            rationale = "Tabs were visited %.1f times on average over the last $lookbackDays days".format(averageVisitCount),
            averageVisitCount = averageVisitCount.toInt(),
        )
    }

    companion object {
        /**
         * The minimum number of tabs a candidate must have for this signal to run at all.
         */
        const val MIN_TABS_FOR_SIGNAL = 2

        /**
         * The default number of days of visit history considered by this signal.
         */
        const val DEFAULT_LOOKBACK_DAYS = 14L

        /**
         * The minimum average visit count across a candidate's tabs for this signal to fire.
         */
        const val MIN_AVERAGE_VISITS = 2.0

        /**
         * The average visit count at, or above, which this signal awards a full score of `1f`.
         */
        const val MAX_VISITS_FOR_FULL_SCORE = 10.0
    }
}
