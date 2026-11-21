package com.parcha.spoilnot.core.designsystem

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.parcha.spoilnot.core.designsystem.component.OFFLINE_BANNER_DELAY_MS
import com.parcha.spoilnot.core.designsystem.component.OfflineBanner
import com.parcha.spoilnot.core.designsystem.theme.ColorTheme
import com.parcha.spoilnot.core.designsystem.theme.SpoilNotTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Offline banner waits before it shows (DESIGN 9.5, after v1, 2026-11-21): a screen reads the cache first and the
 * server a moment later, when it starts and when it opens a new query such as Plan's next week, so a banner for that
 * moment only moved the screen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class OfflineBannerTest {
    @get:Rule
    val compose = createComposeRule()

    private val offline = mutableStateOf(false)

    private fun show() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            SpoilNotTheme(colorTheme = ColorTheme.SpoilNot) { OfflineBanner(offline = offline.value) }
        }
        compose.mainClock.advanceTimeByFrame()
    }

    /** Sets the flag and draws a frame, so the banner's wait starts before the clock moves on. */
    private fun setOffline(value: Boolean) {
        offline.value = value
        // With the clock held, nothing else tells the composition about a write from the test.
        Snapshot.sendApplyNotifications()
        compose.mainClock.advanceTimeByFrame()
    }

    private fun advance(ms: Long) {
        compose.mainClock.advanceTimeBy(ms)
        compose.waitForIdle()
    }

    private fun banner() = compose.onNodeWithTag("offline_banner")

    @Test
    fun aMomentFromTheCacheShowsNothing() {
        show()
        setOffline(true)
        advance(OFFLINE_BANNER_DELAY_MS - 500)
        banner().assertDoesNotExist()
        setOffline(false)
        advance(OFFLINE_BANNER_DELAY_MS * 2)
        banner().assertDoesNotExist()
    }

    @Test
    fun stayingOfflineShowsTheBannerAfterTheWait() {
        show()
        setOffline(true)
        advance(OFFLINE_BANNER_DELAY_MS + 100)
        banner().assertExists()
    }

    @Test
    fun theServerAnsweringHidesItAtOnce() {
        show()
        setOffline(true)
        advance(OFFLINE_BANNER_DELAY_MS + 100)
        banner().assertExists()
        setOffline(false)
        banner().assertDoesNotExist()
    }

    @Test
    fun goingOfflineAgainWaitsAgain() {
        show()
        setOffline(true)
        advance(OFFLINE_BANNER_DELAY_MS + 100)
        setOffline(false)
        advance(100)
        setOffline(true)
        advance(OFFLINE_BANNER_DELAY_MS - 500)
        banner().assertDoesNotExist()
        advance(600)
        banner().assertExists()
    }
}
