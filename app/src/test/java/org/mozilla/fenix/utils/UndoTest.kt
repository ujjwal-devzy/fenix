/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.utils

import android.app.Activity
import io.mockk.every
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mozilla.fenix.ext.settings
import org.mozilla.fenix.helpers.FenixRobolectricTestRunner
import org.robolectric.Robolectric

@RunWith(FenixRobolectricTestRunner::class)
class UndoTest {
    private val activity: Activity = Robolectric.buildActivity(Activity::class.java).create().get()

    @Test
    fun `GIVEN accessibility services are disabled WHEN getting the undo delay THEN return the standard delay in seconds`() {
        every { activity.settings().accessibilityServicesEnabled } returns false

        assertEquals(UNDO_DELAY_SECONDS, activity.getUndoDelay())
    }

    @Test
    fun `GIVEN accessibility services are enabled WHEN getting the undo delay THEN return the longer delay in seconds`() {
        every { activity.settings().accessibilityServicesEnabled } returns true

        assertEquals(ACCESSIBLE_UNDO_DELAY_SECONDS, activity.getUndoDelay())
    }
}
