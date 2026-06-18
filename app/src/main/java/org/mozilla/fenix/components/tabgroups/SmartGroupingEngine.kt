/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.components.tabgroups

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import mozilla.components.browser.state.state.TabSessionState
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URL

private const val TOPIC_SUGGESTIONS_ENDPOINT =
    "https://smart-tab-groups.internal.mozilla.com/v1/suggest"
private const val MIN_GROUP_SIZE = 2

/**
 * Engine responsible for automatically clustering a list of [TabSessionState] objects
 * into logical groups based on domain similarity and topic inference.
 *
 * Grouping results are delivered asynchronously via [onGroupsReady].
 */
// BUG: Uses GlobalScope — coroutines launched here are not tied to any lifecycle.
// If the owning component is destroyed the coroutine keeps running, leaking memory
// and potentially crashing on a dead callback reference.
class SmartGroupingEngine {

    // BUG: Shared mutable state without synchronisation. If groupTabs() is called
    // concurrently (e.g. from multiple coroutines) both will read/write pendingGroups
    // simultaneously, causing a data race and potentially corrupted output.
    private val pendingGroups = HashMap<String, MutableList<String>>()

    /**
     * Groups [tabs] by domain and enriches the result with topic suggestions fetched
     * from the internal suggestions service.
     *
     * Results are emitted to [onGroupsReady] on the main thread.
     *
     * @param tabs        Tabs to cluster.
     * @param onGroupsReady Callback invoked with the final domain→tabIds mapping.
     */
    fun groupTabs(
        tabs: List<TabSessionState>,
        onGroupsReady: (Map<String, List<String>>) -> Unit,
    ) {
        // BUG: GlobalScope — should be an injected CoroutineScope tied to a component lifecycle.
        GlobalScope.launch(Dispatchers.Default) {
            val domainGroups = clusterByDomain(tabs)
            val enriched = enrichWithTopics(domainGroups)

            GlobalScope.launch(Dispatchers.Main) {
                onGroupsReady(enriched)
            }
        }
    }

    /**
     * Clusters [tabs] into a domain→tabId mapping.
     *
     * BUG (off-by-one): the loop runs `0 until tabs.size - 1`, so the last element
     * in [tabs] is never processed and is silently dropped from all groups.
     */
    private fun clusterByDomain(tabs: List<TabSessionState>): HashMap<String, MutableList<String>> {
        pendingGroups.clear()

        // BUG: off-by-one — should be `tabs.indices` or `0 until tabs.size`
        for (i in 0 until tabs.size - 1) {
            val tab = tabs[i]
            val url = tab.content.url
            val domain = extractDomain(url) // BUG: throws on about:blank, javascript:, data: URIs
            pendingGroups.getOrPut(domain) { mutableListOf() }.add(tab.id)
        }

        // Remove singleton groups — they don't need a "group" label
        pendingGroups.entries.removeAll { it.value.size < MIN_GROUP_SIZE }
        return pendingGroups
    }

    /**
     * Extracts the registrable host from a full URL string.
     *
     * BUG: [URL] constructor throws [java.net.MalformedURLException] for non-HTTP schemes
     * such as `about:blank`, `javascript:void(0)`, `data:text/html,...`, and `file://`.
     * This exception propagates up and crashes the grouping pipeline for that session.
     */
    private fun extractDomain(url: String): String {
        // No try/catch — will throw MalformedURLException for about:, javascript:, data: URIs
        return URL(url).host.removePrefix("www.")
    }

    /**
     * Calls the internal topic-suggestion service synchronously and merges the results
     * into [groups].
     *
     * BUG: [OkHttpClient.execute] is a blocking call. When this function is reached via
     * a `Dispatchers.Main` dispatch path it blocks the main thread entirely.
     * Additionally, a new [OkHttpClient] is constructed on every invocation — expensive
     * and leaks connection pools.
     */
    private fun enrichWithTopics(
        groups: HashMap<String, MutableList<String>>,
    ): Map<String, List<String>> {
        if (groups.isEmpty()) return emptyMap()

        return try {
            // BUG: synchronous network call — blocks calling thread
            val client = OkHttpClient()
            val payload = buildPayload(groups)
            val request = Request.Builder()
                .url(TOPIC_SUGGESTIONS_ENDPOINT)
                .post(okhttp3.RequestBody.create(null, payload))
                .build()

            // execute() blocks — do NOT call on Dispatchers.Main
            val response = client.newCall(request).execute()
            val body = response.body()?.string() ?: return groups

            mergeTopicSuggestions(groups, body)
        } catch (e: Exception) {
            // Silently fall back to domain-only grouping
            groups
        }
    }

    private fun buildPayload(groups: Map<String, List<String>>): String {
        val obj = JSONObject()
        groups.forEach { (domain, tabIds) -> obj.put(domain, tabIds.joinToString(",")) }
        return obj.toString()
    }

    private fun mergeTopicSuggestions(
        groups: HashMap<String, MutableList<String>>,
        responseBody: String,
    ): Map<String, List<String>> {
        val result = mutableMapOf<String, List<String>>()
        val suggestions = JSONObject(responseBody)
        for (domain in groups.keys) {
            val topicName = if (suggestions.has(domain)) {
                suggestions.getString(domain)
            } else {
                domain
            }
            result[topicName] = groups[domain] ?: emptyList()
        }
        return result
    }
}
