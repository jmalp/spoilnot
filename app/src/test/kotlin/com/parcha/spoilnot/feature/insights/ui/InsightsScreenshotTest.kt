package com.parcha.spoilnot.feature.insights.ui

import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import com.github.takahirom.roborazzi.captureRoboImage
import com.parcha.spoilnot.core.designsystem.theme.SpoilNotTheme
import com.parcha.spoilnot.feature.insights.domain.Insights
import com.parcha.spoilnot.feature.insights.domain.MonthStats
import com.parcha.spoilnot.feature.pantry.domain.DiscardReason
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Insights screen (DESIGN 5.7) with six months of counts, light and dark, font scale 1.0 and 2.0. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h1500dp-xxhdpi")
class InsightsScreenshotTest(private val dark: Boolean, private val fontScale: Float) {
    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0} font={1}")
        fun params() =
            listOf(arrayOf<Any>(false, 1f), arrayOf<Any>(true, 1f), arrayOf<Any>(false, 2f), arrayOf<Any>(true, 2f))

        private val TODAY = LocalDate.of(2026, 10, 18)

        private fun month(m: Int, used: Int, expired: Int = 0, spoiled: Int = 0, lastThrownDay: Int? = null) =
            MonthStats(
                YearMonth.of(2026, m),
                used,
                mapOf(DiscardReason.Expired to expired, DiscardReason.Spoiled to spoiled).filterValues { it > 0 },
                lastThrownDay?.let { mapOf(it to expired + spoiled) }.orEmpty(),
            )
    }

    @get:Rule
    val compose = createComposeRule()

    private fun capture(name: String, stats: List<MonthStats>) {
        val summary = Insights.summary(stats, TODAY)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                SpoilNotTheme(darkTheme = dark, dynamicColor = false) {
                    Surface { InsightsContent(summary) }
                }
            }
        }
        val suffix = "${if (dark) "dark" else "light"}_font${fontScale.toInt()}"
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/${name}_$suffix.png")
    }

    @Test
    fun sixMonths() = capture(
        "insights",
        listOf(
            month(5, used = 12, expired = 4, spoiled = 1, lastThrownDay = 20),
            month(6, used = 15, expired = 3, lastThrownDay = 11),
            month(7, used = 18, expired = 2, spoiled = 1, lastThrownDay = 25),
            month(8, used = 20, expired = 2, lastThrownDay = 9),
            month(9, used = 22, spoiled = 1, lastThrownDay = 28),
            month(10, used = 11, expired = 1, lastThrownDay = 3),
        ),
    )

    @Test
    fun nothingYet() = capture("insights_empty", emptyList())
}
