package com.parcha.spoilnot.feature.insights.ui

import androidx.compose.material3.Surface
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.parcha.spoilnot.core.designsystem.theme.ColorTheme
import com.parcha.spoilnot.core.designsystem.theme.SpoilNotTheme
import com.parcha.spoilnot.feature.insights.domain.Insights
import com.parcha.spoilnot.feature.insights.domain.MonthStats
import com.parcha.spoilnot.feature.pantry.domain.DiscardReason
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Tapping a month on Insights shows its details; TalkBack reads each month on its own (after v1). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class InsightsMonthsTest {
    @get:Rule
    val compose = createComposeRule()

    private val today = LocalDate.of(2026, 10, 18)
    private val stats = listOf(
        MonthStats(YearMonth.of(2026, 9), 12, mapOf(DiscardReason.Expired to 4, DiscardReason.Spoiled to 1)),
        MonthStats(YearMonth.of(2026, 10), 3),
    )

    @Test
    fun theLatestMonthIsShownFirstAndTappingAnotherShowsIt() {
        compose.setContent {
            SpoilNotTheme(colorTheme = ColorTheme.SpoilNot) {
                Surface { InsightsContent(Insights.summary(stats, today)) }
            }
        }
        val bars = Insights.summary(stats, today).months
        val october = bars.lastIndex
        val september = bars.indexOfFirst { it.month == YearMonth.of(2026, 9) }
        compose.onNodeWithTag("month_bar_$october").assertIsSelected()
        compose.onNodeWithTag("insights_month_details").performScrollTo()
            .assert(hasText("3 used · 0 thrown away · 100% used", substring = true))
        compose.onNodeWithTag("month_bar_$september")
            .assert(hasContentDescription("September: 12 used, 5 thrown away"))
            .performClick()
            .assertIsSelected()
        compose.onNodeWithTag("insights_month_details")
            .assert(hasText("12 used · 5 thrown away · 71% used", substring = true))
            .assert(hasText("Thrown away: Expired 4, Spoiled 1", substring = true))
    }
}
