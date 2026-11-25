package com.parcha.spoilnot.feature.settings.ui

import com.mikepenz.aboutlibraries.Libs
import com.parcha.spoilnot.R
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * About's licence list (DESIGN 5.6) lists the two bundled fonts with their SIL Open Font License, which asks every copy
 * to carry the font's copyright notice and the licence (after v1, review B27, CODE-06). AboutLibraries generates the
 * list from the Maven dependencies; the fonts aren't one, so their definitions are in `app/aboutlibraries`.
 */
@RunWith(RobolectricTestRunner::class)
class AboutLicensesTest {
    private val libraries =
        RuntimeEnvironment.getApplication()
            .resources.openRawResource(R.raw.aboutlibraries)
            .bufferedReader()
            .use { Libs.Builder().withJson(it.readText()).build().libraries }

    @Test
    fun listsBothFontsWithTheirLicence() {
        for ((id, name, copyright) in FONTS) {
            val font = libraries.single { it.uniqueId == id }
            assertEquals(name, font.name)
            val licence = font.licenses.single()
            assertEquals("OFL-1.1", licence.spdxId)
            val text = licence.licenseContent.orEmpty()
            assertTrue("$name's licence starts with its copyright", text.startsWith(copyright))
            assertTrue("$name's licence is the whole OFL", text.contains("SIL OPEN FONT LICENSE Version 1.1"))
            assertTrue(
                "$name's licence is the whole OFL",
                text.contains("5) The Font Software, modified or unmodified"),
            )
        }
    }

    /** The copyright line in each licence is the one in the font file the app bundles (its `name` table, ID 0). */
    @Test
    fun copyrightMatchesTheBundledFont() {
        for ((_, name, copyright) in FONTS) {
            val file = File("src/main/res/font/${name.lowercase()}.ttf")
            assertTrue("$name's font file carries \"$copyright\"", copyright in nameRecords(file.readBytes()))
        }
    }

    /** The Windows (platform 3) name records of a TrueType font, decoded. */
    private fun nameRecords(font: ByteArray): List<String> {
        fun u16(at: Int) = ((font[at].toInt() and 0xFF) shl 8) or (font[at + 1].toInt() and 0xFF)
        fun u32(at: Int) = (u16(at).toLong() shl 16) or u16(at + 2).toLong()
        val tables = u16(4)
        val name = (0 until tables).map { 12 + 16 * it }.first { String(font, it, 4, Charsets.US_ASCII) == "name" }
        val table = u32(name + 8).toInt()
        val count = u16(table + 2)
        val strings = table + u16(table + 4)
        return (0 until count).map { table + 6 + 12 * it }
            .filter { u16(it) == 3 }
            .map { String(font, strings + u16(it + 10), u16(it + 8), Charsets.UTF_16BE) }
    }

    private companion object {
        val FONTS =
            listOf(
                Triple(
                    "font:fredoka",
                    "Fredoka",
                    "Copyright 2016 The Fredoka Project Authors (https://github.com/hafontia/Fredoka-One)",
                ),
                Triple(
                    "font:manrope",
                    "Manrope",
                    "Copyright 2019 The Manrope Project Authors (https://github.com/googlefonts/manrope)",
                ),
            )
    }
}
