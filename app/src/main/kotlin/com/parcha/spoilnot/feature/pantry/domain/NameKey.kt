package com.parcha.spoilnot.feature.pantry.domain

import java.text.Normalizer

/** DESIGN 8.1: lowercased, trimmed, whitespace collapsed, diacritics removed. Used to match and de-duplicate. */
object NameKey {
    private val MARKS = Regex("\\p{Mn}+")

    /**
     * Whitespace, one definition for both apps (after v1, WEB-06): Unicode's White_Space characters (tab to carriage
     * return, space, U+0085, no-break space, U+1680, U+2000 to U+200A, line and paragraph separators, U+202F, U+205F,
     * the full-width space U+3000) and U+FEFF. Written out because Java's `\s` is ASCII only and JavaScript's has
     * U+FEFF but not U+0085. The web's `domain/pantry/rules.ts` has the same class.
     */
    const val WHITESPACE = "\\t\\n\\u000B\\f\\r \\u0085\\u00A0\\u1680\\u2000-\\u200A" +
        "\\u2028\\u2029\\u202F\\u205F\\u3000\\uFEFF"
    private val ONE_SPACE = Regex("[$WHITESPACE]")
    private val SPACES = Regex("[$WHITESPACE]+")
    private val EDGES = Regex("^ +| +$")

    fun of(name: String): String = clean(
        Normalizer.normalize(name, Normalizer.Form.NFD)
            .replace(MARKS, "")
            .lowercase(),
    )

    /** Display form of a typed name: trimmed with whitespace collapsed, case kept. */
    fun clean(name: String): String = name.replace(SPACES, " ").replace(EDGES, "")

    /**
     * [text] with every whitespace character replaced by a plain space, keeping its length, so the parsers' patterns
     * read the same text on both apps and their positions still point into the original.
     */
    fun plainSpaces(text: String): String = text.replace(ONE_SPACE, " ")
}
