/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.components.tabgroupsuggestions.signals

import mozilla.components.lib.publicsuffixlist.PublicSuffixList
import mozilla.components.support.ktx.kotlin.toShortUrl
import org.mozilla.fenix.components.tabgroupsuggestions.CandidateGroupSignal
import org.mozilla.fenix.components.tabgroupsuggestions.CandidateTabGroup
import org.mozilla.fenix.components.tabgroupsuggestions.SuggestionSignal

/**
 * A [CandidateGroupSignal] that scores a candidate group of tabs based on how many of them share
 * the same registrable domain, e.g. `github.com` or `docs.google.com`.
 *
 * This is typically the strongest and cheapest signal available: tabs a user opened while
 * researching or working on the same site are a very common, low-risk grouping to suggest. Other
 * signals ([org.mozilla.fenix.components.tabgroupsuggestions.signals.RecencySignal],
 * [org.mozilla.fenix.components.tabgroupsuggestions.signals.FrequencySignal],
 * [org.mozilla.fenix.components.tabgroupsuggestions.signals.TitleKeywordSignal]) exist to boost or
 * temper the score this signal produces, rather than to replace it.
 *
 * @property publicSuffixList Used to strip the public suffix from each tab's url so that, for
 * example, `mozilla.github.io` and `torproject.github.io` are not treated as the same domain.
 */
class DomainSimilaritySignal(
    private val publicSuffixList: PublicSuffixList,
) : CandidateGroupSignal {

    override suspend fun evaluate(candidate: CandidateTabGroup): SuggestionSignal? {
        if (candidate.tabs.size < MIN_TABS_FOR_SIGNAL) {
            return null
        }

        val domains = candidate.tabs.map { tab -> tab.content.url.toShortUrl(publicSuffixList) }
        val (domain, tabsWithDomain) = domains
            .groupingBy { it }
            .eachCount()
            .filterKeys { it.isNotBlank() }
            .maxByOrNull { (_, count) -> count }
            ?: return null

        val shareRatio = tabsWithDomain.toFloat() / domains.size
        if (shareRatio < MIN_SHARE_RATIO) {
            return null
        }

        return SuggestionSignal.DomainSimilarity(
            score = shareRatio,
            rationale = "$tabsWithDomain of ${domains.size} tabs share the domain \"$domain\"",
            sharedRegistrableDomain = domain,
        )
    }

    companion object {
        /**
         * The minimum number of tabs a candidate must have for this signal to run at all.
         */
        const val MIN_TABS_FOR_SIGNAL = 2

        /**
         * The minimum fraction of a candidate's tabs that must share the same domain for this
         * signal to fire. Below this ratio, the shared domain is considered coincidental rather
         * than meaningful.
         */
        const val MIN_SHARE_RATIO = 0.5f
    }
}
