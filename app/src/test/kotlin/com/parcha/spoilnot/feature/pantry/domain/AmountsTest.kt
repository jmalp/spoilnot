package com.parcha.spoilnot.feature.pantry.domain

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class AmountsTest {
    @Test
    fun `amounts are written in ASCII digits and read back under Arabic and Persian`() {
        // After v1, LOGIC-12: a form pre-filled on such a phone must read its own value back.
        val before = Locale.getDefault()
        try {
            for (tag in listOf("ar-EG", "fa-IR", "hi-IN-u-nu-deva")) {
                Locale.setDefault(Locale.forLanguageTag(tag))
                assertEquals(tag, "1.5", Amounts.format(1.5))
                assertEquals(tag, 1.5, Amounts.parse(Amounts.format(1.5))!!, 0.0)
                // The stepper: + on "1" twice.
                val twice = Amounts.format(Amounts.parse(Amounts.format(Amounts.parse("1")!! + 1))!! + 1)
                assertEquals(tag, "3", twice)
            }
        } finally {
            Locale.setDefault(before)
        }
    }

    @Test
    fun `typed native digits and separators are read`() {
        assertEquals(1.5, Amounts.parse("١٫٥")!!, 0.0)
        assertEquals(2.25, Amounts.parse("۲٫۲۵")!!, 0.0)
        assertEquals(1.5, Amounts.parse("1,5")!!, 0.0)
        assertEquals(null, Amounts.parse("1..5"))
    }
}
