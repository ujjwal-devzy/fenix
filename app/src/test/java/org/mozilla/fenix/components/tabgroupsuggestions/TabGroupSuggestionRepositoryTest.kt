/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.components.tabgroupsuggestions

import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TabGroupSuggestionRepositoryTest {

    @MockK
    private lateinit var preferences: TabGroupSuggestionPreferences

    private lateinit var repository: TabGroupSuggestionRepository

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        repository = DefaultTabGroupSuggestionRepository(preferences)
    }

    @Test
    fun `WHEN dismissedSuggestionIds is called THEN it returns the ids persisted in preferences`() = runTest {
        coEvery { preferences.data() } returns TabGroupSuggestionPreferencesData(
            dismissedSuggestionIds = setOf("a", "b"),
        )

        assertEquals(setOf("a", "b"), repository.dismissedSuggestionIds())
    }

    @Test
    fun `GIVEN nothing has been dismissed WHEN dismissedSuggestionIds is called THEN an empty set is returned`() = runTest {
        coEvery { preferences.data() } returns TabGroupSuggestionPreferencesData()

        assertTrue(repository.dismissedSuggestionIds().isEmpty())
    }

    @Test
    fun `GIVEN preferences data has not been read yet WHEN isDismissed is called THEN it reads through to preferences`() = runTest {
        coEvery { preferences.data() } returns TabGroupSuggestionPreferencesData(
            dismissedSuggestionIds = setOf("dismissed-id"),
        )

        repository.isDismissed("dismissed-id")

        coVerify(exactly = 1) { preferences.data() }
    }

    @Test
    fun `GIVEN a dismissed suggestion id WHEN isDismissed is called THEN it returns true`() = runTest {
        coEvery { preferences.data() } returns TabGroupSuggestionPreferencesData(
            dismissedSuggestionIds = setOf("dismissed-id"),
        )

        assertTrue(repository.isDismissed("dismissed-id"))
    }

    @Test
    fun `GIVEN a suggestion id that was not dismissed WHEN isDismissed is called THEN it returns false`() = runTest {
        coEvery { preferences.data() } returns TabGroupSuggestionPreferencesData(
            dismissedSuggestionIds = setOf("other-id"),
        )

        assertFalse(repository.isDismissed("not-dismissed-id"))
    }

    @Test
    fun `GIVEN a suggestion id that was already dismissed WHEN dismiss is called again THEN the set is unaffected`() = runTest {
        val transform = slot<(TabGroupSuggestionPreferencesData) -> TabGroupSuggestionPreferencesData>()
        coEvery { preferences.update(capture(transform)) } returns TabGroupSuggestionPreferencesData()

        repository.dismiss("already-dismissed")

        val current = TabGroupSuggestionPreferencesData(dismissedSuggestionIds = setOf("already-dismissed"))
        val updated = transform.captured(current)

        assertEquals(setOf("already-dismissed"), updated.dismissedSuggestionIds)
    }

    @Test
    fun `GIVEN a suggestion id WHEN dismiss is called THEN it is added to the persisted dismissed set`() = runTest {
        val transform = slot<(TabGroupSuggestionPreferencesData) -> TabGroupSuggestionPreferencesData>()
        coEvery { preferences.update(capture(transform)) } returns TabGroupSuggestionPreferencesData()

        repository.dismiss("new-id")

        val current = TabGroupSuggestionPreferencesData(dismissedSuggestionIds = setOf("existing-id"))
        val updated = transform.captured(current)

        assertEquals(setOf("existing-id", "new-id"), updated.dismissedSuggestionIds)
    }

    @Test
    fun `GIVEN a suggestion id and timestamp WHEN recordShown is called THEN the timestamp is persisted`() = runTest {
        val transform = slot<(TabGroupSuggestionPreferencesData) -> TabGroupSuggestionPreferencesData>()
        coEvery { preferences.update(capture(transform)) } returns TabGroupSuggestionPreferencesData()

        repository.recordShown("suggestion-1", 42L)

        val current = TabGroupSuggestionPreferencesData(lastShownTimestamps = mapOf("other" to 1L))
        val updated = transform.captured(current)

        assertEquals(mapOf("other" to 1L, "suggestion-1" to 42L), updated.lastShownTimestamps)
    }

    @Test
    fun `GIVEN recordShown was called for the same suggestion twice WHEN the transform is applied THEN the newer timestamp overwrites the older one`() = runTest {
        val transform = slot<(TabGroupSuggestionPreferencesData) -> TabGroupSuggestionPreferencesData>()
        coEvery { preferences.update(capture(transform)) } returns TabGroupSuggestionPreferencesData()

        repository.recordShown("suggestion-1", 99L)

        val current = TabGroupSuggestionPreferencesData(lastShownTimestamps = mapOf("suggestion-1" to 1L))
        val updated = transform.captured(current)

        assertEquals(99L, updated.lastShownTimestamps["suggestion-1"])
    }

    @Test
    fun `GIVEN a suggestion that has been shown WHEN lastShownAt is called THEN its timestamp is returned`() = runTest {
        coEvery { preferences.data() } returns TabGroupSuggestionPreferencesData(
            lastShownTimestamps = mapOf("suggestion-1" to 555L),
        )

        assertEquals(555L, repository.lastShownAt("suggestion-1"))
    }

    @Test
    fun `GIVEN a suggestion that has never been shown WHEN lastShownAt is called THEN null is returned`() = runTest {
        coEvery { preferences.data() } returns TabGroupSuggestionPreferencesData()

        assertNull(repository.lastShownAt("never-shown"))
    }

    @Test
    fun `GIVEN a suggestion shown at timestamp zero WHEN lastShownAt is called THEN zero is still returned rather than null`() = runTest {
        coEvery { preferences.data() } returns TabGroupSuggestionPreferencesData(
            lastShownTimestamps = mapOf("suggestion-1" to 0L),
        )

        assertEquals(0L, repository.lastShownAt("suggestion-1"))
    }

    @Test
    fun `WHEN clearDismissed is called THEN the dismissed set is emptied`() = runTest {
        val transform = slot<(TabGroupSuggestionPreferencesData) -> TabGroupSuggestionPreferencesData>()
        coEvery { preferences.update(capture(transform)) } returns TabGroupSuggestionPreferencesData()

        repository.clearDismissed()

        val current = TabGroupSuggestionPreferencesData(dismissedSuggestionIds = setOf("a", "b"))
        val updated = transform.captured(current)

        assertTrue(updated.dismissedSuggestionIds.isEmpty())
    }

    @Test
    fun `WHEN clearDismissed is called THEN last shown timestamps are left untouched`() = runTest {
        val transform = slot<(TabGroupSuggestionPreferencesData) -> TabGroupSuggestionPreferencesData>()
        coEvery { preferences.update(capture(transform)) } returns TabGroupSuggestionPreferencesData()

        repository.clearDismissed()

        val current = TabGroupSuggestionPreferencesData(
            dismissedSuggestionIds = setOf("a"),
            lastShownTimestamps = mapOf("a" to 1L),
        )
        val updated = transform.captured(current)

        assertEquals(mapOf("a" to 1L), updated.lastShownTimestamps)
    }
}
