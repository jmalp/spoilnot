package com.parcha.spoilnot.core.designsystem.theme

import com.parcha.spoilnot.feature.recipes.domain.Scaling
import java.awt.Font
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * The bundled fonts are subsets (after v1, review EFF-07, `scripts/subset-fonts.sh`, DECISIONS 2026-11-22). A
 * character a subset lacks falls back to the phone's own font, so it still shows, but in another face. This test
 * fails when the app's text uses a character that Manrope has and its subset dropped: every string (the app's, and
 * the web app's generated ones), the characters the code puts into text (every string and character literal in
 * `src/main`, so "×", "·" and amounts such as "1½"), and the app's name in Fredoka.
 */
class FontSubsetTest {
    // Gradle runs unit tests in the module's folder (app/).
    private val manrope = font("src/main/res/font/manrope.ttf")
    private val fredoka = font("src/main/res/font/fredoka.ttf")
    private val fullManrope = font("../tools/fonts/manrope.ttf")

    @Test
    fun `Manrope's subset has every character of the app's strings`() {
        val strings = File("src/main/res").listFiles { file -> file.name.startsWith("values") }!!
            .map { File(it, "strings.xml") }
            .filter { it.exists() }
        assertTrue(strings.isNotEmpty())
        assertCovered("strings.xml", strings.flatMap { stringResources(it) }.joinToString(""))
    }

    @Test
    fun `Manrope's subset has every character of the web app's strings`() {
        // The web draws Manrope from its own files (@fontsource-variable), but its strings come from the same
        // sources and should not need anything the app's font lacks.
        assertCovered("web strings", File("../web/src/strings.generated.ts").readText())
        assertCovered("web-only strings", stringResources(File("../web/strings-web.xml")).joinToString(""))
    }

    @Test
    fun `Manrope's subset has every character the code puts into text`() {
        val literals = File("src/main/kotlin").walk().filter { it.extension == "kt" }
            .joinToString("") { literalText(it.readText()) }
        assertCovered("Kotlin literals in src/main", literals)
    }

    @Test
    fun `amounts show in Manrope or in the phone's font`() {
        val amounts = (0..80).joinToString(" ") { Scaling.formatQuantity(it / 24.0) }
        assertTrue(amounts.contains('½') && amounts.contains('⅓'))
        assertCovered("amounts", amounts)
    }

    @Test
    fun `the app's name is drawn in Fredoka`() {
        val names = Regex("""resValue\("string", "app_name", "([^"]+)"\)""")
            .findAll(File("build.gradle.kts").readText())
            .map { it.groupValues[1] }
            .toList()
        assertEquals(listOf("Spoil Not Dev", "Spoil Not"), names)
        for (name in names) {
            val missing = name.codePoints().toArray().filterNot { fredoka.canDisplay(it) }
            assertTrue("Fredoka's subset lacks ${describe(missing)} of \"$name\"", missing.isEmpty())
        }
    }

    @Test
    fun `characters left to the phone's font are missing from the full Manrope too`() {
        // So this list cannot hide a character that the subset dropped.
        for (char in SYSTEM_FONT.codePoints()) assertFalse(describe(listOf(char)), fullManrope.canDisplay(char))
    }

    private fun assertCovered(source: String, text: String) {
        val missing = text.codePoints().toArray().distinct().filter { char ->
            !manrope.canDisplay(char) && !ignored(char)
        }
        assertTrue(
            "$source uses ${describe(missing)}, which Manrope's subset lacks: add it to scripts/subset-fonts.sh and " +
                "run the script, or list it in FontSubsetTest if the app's font never had it",
            missing.isEmpty(),
        )
    }

    private fun ignored(char: Int): Boolean = Character.isISOControl(char) || Character.isWhitespace(char) ||
        Character.getType(char) in setOf(Character.SPACE_SEPARATOR, Character.FORMAT).map { it.toInt() } ||
        SYSTEM_FONT.contains(Character.toString(char)) || READ_NOT_SHOWN.contains(Character.toString(char))

    private fun font(path: String): Font = File(path).inputStream().use { Font.createFont(Font.TRUETYPE_FONT, it) }

    private fun describe(chars: List<Int>) = chars.joinToString { "\"${Character.toString(it)}\" (U+%04X)".format(it) }

    /** The text of every string, plural and array item, with Android's escapes undone. */
    private fun stringResources(file: File): List<String> {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val elements = listOf("string", "item").flatMap { tag ->
            val nodes = document.getElementsByTagName(tag)
            (0 until nodes.length).map { nodes.item(it) as Element }
        }
        return elements.map { element ->
            element.textContent.replace(Regex("""\\u([0-9a-fA-F]{4})""")) {
                it.groupValues[1].toInt(16).toChar().toString()
            }
                .replace(Regex("""\\(.)"""), "$1")
        }
    }

    /** The contents of the string and character literals in Kotlin source, comments left out. */
    private fun literalText(source: String): String {
        val out = StringBuilder()
        var i = 0
        while (i < source.length) {
            when {
                source.startsWith("//", i) -> i = source.indexOf('\n', i).let { if (it < 0) source.length else it }

                source.startsWith("/*", i) ->
                    i =
                        source.indexOf("*/", i + 2).let { if (it < 0) source.length else it + 2 }

                source.startsWith("\"\"\"", i) -> {
                    val end = source.indexOf("\"\"\"", i + 3).let { if (it < 0) source.length else it }
                    out.append(source, i + 3, end)
                    i = end + 3
                }

                source[i] == '"' || source[i] == '\'' -> {
                    val quote = source[i]
                    i++
                    while (i < source.length && source[i] != quote && source[i] != '\n') {
                        if (source[i] == '\\' && i + 1 < source.length) {
                            if (source[i + 1] == 'u' && i + 5 < source.length) {
                                out.append(source.substring(i + 2, i + 6).toInt(16).toChar())
                                i += 6
                            } else {
                                i += 2
                            }
                        } else {
                            out.append(source[i++])
                        }
                    }
                    i++
                }

                else -> i++
            }
        }
        return out.toString()
    }

    private companion object {
        /**
         * Characters the full Manrope never had, so phones always drew them in their own font: the ⋮ of the tour's
         * text and the fractions of amounts other than ½, ¼ and ¾.
         */
        const val SYSTEM_FONT = "⋮⅓⅔⅕⅖⅗⅘⅙⅚⅛⅜⅝⅞"

        /** Read in typed amounts (the Arabic decimal and thousands separators), never shown. */
        const val READ_NOT_SHOWN = "٫،"
    }
}
