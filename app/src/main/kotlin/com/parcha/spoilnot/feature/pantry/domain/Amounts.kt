package com.parcha.spoilnot.feature.pantry.domain

import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * Amounts as people read and type them (after v1, LOGIC-12). They are always written with ASCII digits and a point,
 * whatever the phone's language, so a form pre-filled with "1.5" reads back as 1.5; a phone in Arabic or Persian
 * would otherwise show "١٫٥", which no field could read. Typed amounts may use the phone's own digits and a comma.
 * The web's `domain/pantry/amounts.ts` has the same rules.
 */
object Amounts {
    private val FORMAT = ThreadLocal.withInitial {
        DecimalFormat("0.##", DecimalFormatSymbols(Locale.ROOT)).apply { roundingMode = RoundingMode.HALF_UP }
    }
    private val NUMBER = Regex("""^\d*\.?\d+$|^\d+\.$""")

    /**
     * Where each set of ten decimal digits starts in the Basic Multilingual Plane besides ASCII (Unicode's Nd):
     * Arabic-Indic, Persian, NKo, Devanagari, Bengali and the other Indic scripts, Thai, Lao, Tibetan, Myanmar, Khmer,
     * Mongolian, the Southeast Asian scripts, Vai, Saurashtra, Kayah Li, Javanese, Cham, Meetei Mayek and full-width.
     * The same list as the web's, so both apps read the same typed digits.
     */
    private val DIGIT_ZEROS = intArrayOf(
        0x0660, 0x06F0, 0x07C0, 0x0966, 0x09E6, 0x0A66, 0x0AE6, 0x0B66, 0x0BE6, 0x0C66, 0x0CE6, 0x0D66, 0x0DE6,
        0x0E50, 0x0ED0, 0x0F20, 0x1040, 0x1090, 0x17E0, 0x1810, 0x1946, 0x19D0, 0x1A80, 0x1A90, 0x1B50, 0x1BB0,
        0x1C40, 0x1C50, 0xA620, 0xA8D0, 0xA900, 0xA9D0, 0xA9F0, 0xAA50, 0xABF0, 0xFF10,
    )

    private fun digit(c: Char): Int? = DIGIT_ZEROS.firstOrNull { c.code in it..it + 9 }?.let { c.code - it }

    /** 2, 1.5 or 0.25: no trailing zeros, at most two decimals, halves rounded up (as the web's `toFixed`). */
    fun format(value: Double): String = FORMAT.get()!!.format(value)

    /** A typed amount, or null when it is not a number: "1.5", "1,5", "١٫٥" and "۱.۵" are all 1.5. */
    fun parse(text: String): Double? {
        val ascii = buildString {
            for (c in NameKey.clean(text)) {
                val digit = digit(c)
                append(
                    when {
                        digit != null -> '0' + digit
                        c == ',' || c == '٫' || c == '،' -> '.'
                        else -> c
                    },
                )
            }
        }
        return ascii.takeIf { NUMBER.matches(it) }?.toDoubleOrNull()
    }
}
