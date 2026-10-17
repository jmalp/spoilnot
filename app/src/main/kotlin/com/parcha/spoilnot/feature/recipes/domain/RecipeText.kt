package com.parcha.spoilnot.feature.recipes.domain

import com.parcha.spoilnot.feature.pantry.domain.NameKey
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode

/** Sanitizing imported text (DESIGN 11.6): tags stripped, entities decoded, whitespace collapsed, length capped. */
object RecipeText {
    private val SPACES = Regex("""[\s\u00A0]+""")
    private val ENTITY = Regex("""&(#\d+|#x[0-9a-fA-F]+|[a-zA-Z]+);""")
    private val STEP_NUMBER =
        Regex("""^(?:step\s*)?\d{1,2}\s*[.):]\s+|^step\s*\d{1,2}\s*[:.-]?\s*""", RegexOption.IGNORE_CASE)

    /** One line of plain text, or null when nothing is left. */
    fun clean(text: String?, limit: Int): String? {
        if (text == null) return null
        var plain = if ('<' in text || '&' in text) Jsoup.parseBodyFragment(text).text() else text
        // Some sites encode twice ("&amp;amp;").
        if (ENTITY.containsMatchIn(plain)) plain = Jsoup.parseBodyFragment(plain).text()
        return plain.replace(SPACES, " ").trim().take(limit).trim().ifEmpty { null }
    }

    /** Text that may hold several lines (instructions given as one string or as HTML), one entry per line. */
    fun lines(text: String?, limit: Int): List<String> {
        if (text == null) return emptyList()
        val withBreaks = if ('<' in text) blockText(Jsoup.parseBodyFragment(text).body()) else text
        return withBreaks.split('\n', '\r').mapNotNull { clean(it, limit) }
    }

    /** An element's text with line breaks at block boundaries and `<br>`. */
    fun blockText(element: Element): String {
        val out = StringBuilder()
        fun walk(e: Element) {
            for (node in e.childNodes()) {
                when (node) {
                    is TextNode -> out.append(node.text())

                    is Element -> {
                        val block = node.isBlock || node.normalName() == "br"
                        if (block) out.append('\n')
                        walk(node)
                        if (block) out.append('\n')
                    }
                }
            }
        }
        walk(element)
        return out.toString()
    }

    /** "1. Preheat the oven" becomes "Preheat the oven": the app numbers steps itself. */
    fun stripStepNumber(step: String): String = step.replace(STEP_NUMBER, "").ifBlank { step }

    /** Up to [RecipeLimits.MAX_TAGS] distinct tags, in order. */
    fun tags(values: List<String>): List<String> = values
        .flatMap { it.split(',') }
        .mapNotNull { clean(it, RecipeLimits.TAG) }
        .distinctBy { NameKey.of(it) }
        .take(RecipeLimits.MAX_TAGS)
}

/** ISO 8601 durations such as PT1H30M (DESIGN 11.3), and the plain-text forms some sites use instead. */
object Durations {
    private val ISO = Regex(
        """^P(?:\d+Y)?(?:\d+M)?(?:(\d+)W)?(?:(\d+(?:\.\d+)?)D)?""" +
            """(?:T(?:(\d+(?:\.\d+)?)H)?(?:(\d+(?:\.\d+)?)M)?(?:(\d+(?:\.\d+)?)S)?)?$""",
        RegexOption.IGNORE_CASE,
    )
    private val HOURS = Regex("""(\d+(?:\.\d+)?)\s*(?:hours?|hrs?|h)\b""", RegexOption.IGNORE_CASE)
    private val MINUTES = Regex("""(\d+)\s*(?:minutes?|mins?|m)\b""", RegexOption.IGNORE_CASE)

    /** Whole minutes, or null when unknown or zero. */
    fun minutes(text: String?): Int? {
        val t = text?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val iso = ISO.matchEntire(t)
        val total = if (iso != null) {
            val (w, d, h, m, s) = iso.destructured
            (w.num() * 7 + d.num()) * 1440 + h.num() * 60 + m.num() + s.num() / 60
        } else {
            val h = HOURS.find(t)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
            val m = MINUTES.find(t)?.groupValues?.get(1)?.toDoubleOrNull()
                ?: t.toDoubleOrNull().takeIf { h == 0.0 }
                ?: 0.0
            h * 60 + m
        }
        val rounded = Math.round(total).toInt()
        return rounded.takeIf { it in 1..(60 * 24 * 30) }
    }

    private fun String.num() = toDoubleOrNull() ?: 0.0
}

/** recipeYield: "4", "4 servings", "Makes 12 cookies", "4-6" (DESIGN 11.3: first number, and keep the text). */
object Yield {
    private val FIRST_NUMBER = Regex("""\d+""")

    fun of(values: List<String>): Pair<Int?, String?> {
        val texts = values.mapNotNull { RecipeText.clean(it, RecipeLimits.SERVINGS_TEXT) }
        val servings = texts.firstNotNullOfOrNull { FIRST_NUMBER.find(it)?.value?.toIntOrNull() }
            ?.takeIf { it in 1..1000 }
        // Prefer the wording ("4 servings") over a bare number.
        val text = texts.firstOrNull { t -> t.any { it.isLetter() } } ?: texts.firstOrNull()
        return servings to text
    }
}
