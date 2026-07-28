/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.components.tabgroupsuggestions.signals

import androidx.annotation.VisibleForTesting
import org.mozilla.fenix.components.tabgroupsuggestions.CandidateGroupSignal
import org.mozilla.fenix.components.tabgroupsuggestions.CandidateTabGroup
import org.mozilla.fenix.components.tabgroupsuggestions.SuggestionSignal

/**
 * A [CandidateGroupSignal] that scores a candidate group of tabs based on how many meaningful
 * keywords their page titles have in common.
 *
 * This signal exists to catch groupings that
 * [org.mozilla.fenix.components.tabgroupsuggestions.signals.DomainSimilaritySignal] would miss:
 * for example, several news articles about the same topic hosted on different domains, or
 * documentation pages for the same product split across a vendor site and a community wiki.
 *
 * @property stopWords Common words excluded from keyword extraction because they carry little
 * topical meaning on their own, e.g. "the" or "and".
 */
class TitleKeywordSignal(
    private val stopWords: Set<String> = DEFAULT_STOP_WORDS,
) : CandidateGroupSignal {

    override suspend fun evaluate(candidate: CandidateTabGroup): SuggestionSignal? {
        if (candidate.tabs.size < MIN_TABS_FOR_SIGNAL) {
            return null
        }

        val keywordSets = candidate.tabs.map { tab -> extractKeywords(tab.content.title) }
        if (keywordSets.any { it.isEmpty() }) {
            return null
        }

        val sharedKeywords = keywordSets.reduce { acc, keywords -> acc.intersect(keywords) }
        if (sharedKeywords.isEmpty()) {
            return null
        }

        val allKeywords = keywordSets.reduce { acc, keywords -> acc + keywords }
        val overlapRatio = sharedKeywords.size.toFloat() / allKeywords.size

        if (overlapRatio < MIN_OVERLAP_RATIO) {
            return null
        }

        return SuggestionSignal.TitleKeywordOverlap(
            score = overlapRatio.coerceIn(SuggestionSignal.MIN_SCORE, SuggestionSignal.MAX_SCORE),
            rationale = "Titles share the keyword(s): ${sharedKeywords.joinToString()}",
            sharedKeywords = sharedKeywords.sorted(),
        )
    }

    /**
     * Splits [title] into lowercase, alphanumeric tokens and filters out anything too short to be
     * meaningful or present in [stopWords].
     */
    @VisibleForTesting
    internal fun extractKeywords(title: String): Set<String> {
        return TOKEN_DELIMITERS.split(title.lowercase())
            .map { token -> token.trim() }
            .filter { token -> token.length >= MIN_KEYWORD_LENGTH && token !in stopWords }
            .toSet()
    }

    companion object {
        /**
         * The minimum number of tabs a candidate must have for this signal to run at all.
         */
        const val MIN_TABS_FOR_SIGNAL = 2

        /**
         * The minimum ratio of shared to total keywords across a candidate's tabs for this signal
         * to fire.
         */
        const val MIN_OVERLAP_RATIO = 0.2f

        /**
         * The minimum length, in characters, for a token to be considered a meaningful keyword.
         */
        const val MIN_KEYWORD_LENGTH = 3

        private val TOKEN_DELIMITERS = Regex("[^\\p{L}\\p{Nd}]+")

        /**
         * Common English words excluded from keyword extraction. Kept intentionally small: this
         * signal is meant as a lightweight boost on top of the other signals, not a full natural
         * language processing pipeline.
         */
        val DEFAULT_STOP_WORDS = setOf(
            "the", "a", "an", "and", "or", "of", "to", "in", "for", "on", "with",
            "is", "at", "by", "from", "how", "what", "why", "your", "you", "this",
            "that", "it", "as", "are", "be", "will", "can", "not", "new", "vs",
        )
    }
}
