package com.parcha.spoilnot.feature.pantry.domain

import java.text.Normalizer

/** DESIGN 8.1: lowercased, trimmed, whitespace collapsed, diacritics removed. Used to match and de-duplicate. */
object NameKey {
    private val MARKS = Regex("\\p{Mn}+")
    private val SPACES = Regex("\\s+")

    fun of(name: String): String = Normalizer.normalize(name, Normalizer.Form.NFD)
        .replace(MARKS, "")
        .lowercase()
        .trim()
        .replace(SPACES, " ")

    /** Display form of a typed name: trimmed with whitespace collapsed, case kept. */
    fun clean(name: String): String = name.trim().replace(SPACES, " ")
}
