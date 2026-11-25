package com.parcha.spoilnot.feature.home.ui

import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Home's line above its title is today, as the phone's language writes it (DESIGN 19.7). */
@RunWith(RobolectricTestRunner::class)
class HomeDateTest {
    private val saturday = LocalDate.of(2026, 11, 14)

    @Test
    fun `American English writes the weekday, the month and the day`() {
        assertEquals("Saturday, Nov 14", homeDate(saturday, Locale.US))
    }

    @Test
    fun `British English puts the day before the month`() {
        val text = homeDate(saturday, Locale.UK)
        assertTrue(text, text.startsWith("Saturday"))
        assertTrue(text, text.indexOf("14") < text.indexOf("Nov"))
    }

    @Test
    fun `another language uses its own words`() {
        val text = homeDate(saturday, Locale.forLanguageTag("es-ES"))
        assertTrue(text, text.contains("sábado"))
        assertTrue(text, text.contains("14"))
    }
}
