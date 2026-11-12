package com.parcha.spoilnot.core.ui

import android.content.ClipDescription
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class SensitiveClipTest {
    @Test
    fun `the clip is marked sensitive and labeled, not named by its text`() {
        val clip = sensitiveClip("Invite code", "ABCDE-FGHJK")
        assertEquals("Invite code", clip.description.label)
        assertEquals("ABCDE-FGHJK", clip.getItemAt(0).text)
        assertTrue(clip.description.extras.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE))
    }

    @Test
    @Config(sdk = [32])
    fun `before Android 13 it carries the same key`() {
        val clip = sensitiveClip("Invite code", "ABCDE-FGHJK")
        assertTrue(clip.description.extras.getBoolean("android.content.extra.IS_SENSITIVE"))
    }
}
