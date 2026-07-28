/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.components.tabgroupsuggestions

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mozilla.fenix.helpers.FenixRobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

@RunWith(FenixRobolectricTestRunner::class)
class TabGroupSuggestionPreferencesTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun `WHEN data is read for the first time THEN the default values are returned`() = runTest {
        val preferences = TabGroupSuggestionPreferences(context)

        val data = preferences.data()

        assertEquals(emptySet<String>(), data.dismissedSuggestionIds)
        assertEquals(emptyMap<String, Long>(), data.lastShownTimestamps)
    }

    @Test
    fun `GIVEN two preferences instances sharing a context WHEN one updates THEN the other observes the change`() = runTest {
        val first = TabGroupSuggestionPreferences(context)
        val second = TabGroupSuggestionPreferences(context)

        first.update { current -> current.copy(dismissedSuggestionIds = setOf("suggestion-1")) }

        assertEquals(setOf("suggestion-1"), second.data().dismissedSuggestionIds)
    }

    @Test
    fun `GIVEN several sequential updates WHEN data is read THEN all of them are reflected`() = runTest {
        val preferences = TabGroupSuggestionPreferences(context)

        repeat(3) { index ->
            preferences.update { current ->
                current.copy(dismissedSuggestionIds = current.dismissedSuggestionIds + "suggestion-$index")
            }
        }

        assertEquals(
            setOf("suggestion-0", "suggestion-1", "suggestion-2"),
            preferences.data().dismissedSuggestionIds,
        )
    }

    @Test
    fun `GIVEN an update adding a dismissed id WHEN data is read again THEN the change is visible`() = runTest {
        val preferences = TabGroupSuggestionPreferences(context)

        preferences.update { current ->
            current.copy(dismissedSuggestionIds = current.dismissedSuggestionIds + "suggestion-1")
        }

        assertEquals(setOf("suggestion-1"), preferences.data().dismissedSuggestionIds)
    }

    @Test
    fun `GIVEN updates to different fields WHEN data is read again THEN both changes are reflected`() = runTest {
        val preferences = TabGroupSuggestionPreferences(context)

        preferences.update { it.copy(dismissedSuggestionIds = it.dismissedSuggestionIds + "suggestion-1") }
        preferences.update { it.copy(lastShownTimestamps = it.lastShownTimestamps + ("suggestion-1" to 100L)) }

        val data = preferences.data()
        assertEquals(setOf("suggestion-1"), data.dismissedSuggestionIds)
        assertEquals(mapOf("suggestion-1" to 100L), data.lastShownTimestamps)
    }

    @Test
    fun `WHEN update is called THEN it returns the newly persisted value`() = runTest {
        val preferences = TabGroupSuggestionPreferences(context)

        val result = preferences.update { it.copy(dismissedSuggestionIds = setOf("suggestion-1")) }

        assertEquals(setOf("suggestion-1"), result.dismissedSuggestionIds)
    }

    @Test
    fun `GIVEN a value removed by a later update WHEN data is read THEN it is no longer present`() = runTest {
        val preferences = TabGroupSuggestionPreferences(context)
        preferences.update { it.copy(dismissedSuggestionIds = setOf("suggestion-1", "suggestion-2")) }

        preferences.update { it.copy(dismissedSuggestionIds = it.dismissedSuggestionIds - "suggestion-1") }

        assertEquals(setOf("suggestion-2"), preferences.data().dismissedSuggestionIds)
    }

    @Test
    fun `WHEN the serializer's default value is requested THEN it has no dismissed ids or shown timestamps`() {
        val default = TabGroupSuggestionPreferencesSerializer.defaultValue

        assertEquals(TabGroupSuggestionPreferencesData(), default)
    }

    @Test
    fun `GIVEN data written by the serializer WHEN it is read back THEN the same data is returned`() = runTest {
        val original = TabGroupSuggestionPreferencesData(
            dismissedSuggestionIds = setOf("1", "2"),
            lastShownTimestamps = mapOf("1" to 10L, "2" to 20L),
        )
        val output = ByteArrayOutputStream()

        TabGroupSuggestionPreferencesSerializer.writeTo(original, output)
        val result = TabGroupSuggestionPreferencesSerializer.readFrom(ByteArrayInputStream(output.toByteArray()))

        assertEquals(original, result)
    }

    @Test
    fun `GIVEN no dismissed ids or timestamps WHEN written and read back THEN empty collections round-trip correctly`() = runTest {
        val original = TabGroupSuggestionPreferencesData()
        val output = ByteArrayOutputStream()

        TabGroupSuggestionPreferencesSerializer.writeTo(original, output)
        val result = TabGroupSuggestionPreferencesSerializer.readFrom(ByteArrayInputStream(output.toByteArray()))

        assertEquals(original, result)
    }

    @Test
    fun `GIVEN malformed JSON WHEN readFrom is called THEN the default value is returned instead of throwing`() = runTest {
        val malformed = ByteArrayInputStream("not valid json".toByteArray())

        val result = TabGroupSuggestionPreferencesSerializer.readFrom(malformed)

        assertEquals(TabGroupSuggestionPreferencesSerializer.defaultValue, result)
    }
}
