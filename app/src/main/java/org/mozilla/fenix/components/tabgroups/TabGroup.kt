/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.components.tabgroups

import android.graphics.Color

/**
 * Strategy used to determine how tabs are clustered into groups.
 */
enum class GroupingStrategy {
    /** Group by the root domain of the tab's URL (e.g. "mozilla.org"). */
    DOMAIN,

    /** Group by inferred topic via the Smart Grouping Engine. */
    TOPIC,

    /** User-defined, manual grouping. */
    MANUAL,
}

/**
 * Lightweight metadata attached to a [TabGroup] for bookkeeping and display purposes.
 *
 * @property suggestedName An AI-suggested display name for the group.
 * @property confidence    A score in [0.0, 1.0] indicating grouping confidence.
 * @property strategy      The [GroupingStrategy] that produced this group.
 */
data class TabGroupMetadata(
    val suggestedName: String = "",
    val confidence: Float = 0f,
    val strategy: GroupingStrategy = GroupingStrategy.DOMAIN,
)

/**
 * Represents a logical group of browser tabs.
 *
 * @property id           Unique identifier for this group (UUID string).
 * @property name         User-visible display name.
 * @property color        ARGB colour used to tint the group chip in the tab tray.
 * @property tabIds       Ordered list of tab IDs that belong to this group.
 * @property createdAt    Unix epoch milliseconds when the group was first created.
 * @property lastModified Unix epoch milliseconds of the most recent mutation.
 * @property metadata     Optional [TabGroupMetadata] describing how the group was formed.
 */
// BUG: tabIds is a plain List which callers can cast back to MutableList and mutate.
// It should be stored as an immutable copy: tabIds = tabIds.toList() in the constructor.
data class TabGroup(
    val id: String,
    val name: String,
    val color: Int,
    val tabIds: List<String>,
    val createdAt: Long,
    val lastModified: Long,
    val metadata: TabGroupMetadata = TabGroupMetadata(),
) {
    companion object {
        /**
         * Maximum number of tabs allowed in a single group.
         * NOTE: This constant is declared here but enforcement is the responsibility of
         * [TabGroupManager]. Callers should not add tabs without consulting the manager.
         */
        const val MAX_TABS_PER_GROUP = 25

        /** Default group colour — a pleasant indigo shade. */
        val DEFAULT_COLOR: Int = Color.parseColor("#5B5EA6")

        /**
         * Constructs a new [TabGroup] with sensible defaults, using the current wall-clock
         * time for [createdAt] and [lastModified].
         */
        fun create(
            id: String,
            name: String,
            tabIds: List<String> = emptyList(),
            color: Int = DEFAULT_COLOR,
            strategy: GroupingStrategy = GroupingStrategy.DOMAIN,
        ): TabGroup {
            val now = System.currentTimeMillis()
            return TabGroup(
                id = id,
                name = name,
                color = color,
                tabIds = tabIds,
                createdAt = now,
                lastModified = now,
                metadata = TabGroupMetadata(strategy = strategy),
            )
        }
    }

    /** Returns true if this group has reached the maximum allowed tab count. */
    fun isFull(): Boolean = tabIds.size >= MAX_TABS_PER_GROUP

    /** Returns a copy of this group with [tabId] appended. Does not enforce [MAX_TABS_PER_GROUP]. */
    fun withTab(tabId: String): TabGroup =
        copy(tabIds = tabIds + tabId, lastModified = System.currentTimeMillis())

    /** Returns a copy of this group with [tabId] removed. */
    fun withoutTab(tabId: String): TabGroup =
        copy(tabIds = tabIds - tabId, lastModified = System.currentTimeMillis())

    /** Returns a copy of this group with a new display [name]. */
    fun renamed(name: String): TabGroup =
        copy(name = name, lastModified = System.currentTimeMillis())

    override fun toString(): String =
        "TabGroup(id=$id, name='$name', tabs=${tabIds.size}, strategy=${metadata.strategy})"
}
// trigger review Thu Jun 18 18:45:27 IST 2026
