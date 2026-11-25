package com.parcha.spoilnot.feature.pantry.domain

/**
 * How a food's name is saved (DESIGN 8.1; owner, 2026-11-21): every word starts with a capital letter and keeps the
 * rest as typed, so "black beans" and "Black beans" are both saved as "Black Beans", and "BBQ sauce" as "BBQ Sauce".
 * Used for pantry items and grocery items, whoever adds them; not for recipe titles, lists or people. The web's
 * `domain/pantry/nameCase.ts` does the same.
 */
object NameCase {
    /**
     * [name], already cleaned ([NameKey.clean]), with each word's first letter in capitals. A word starts after a
     * space; punctuation before its first letter keeps it open ("(organic)" becomes "(Organic)"), a digit closes it
     * ("2kg" stays). A letter with no single capital (ß) is kept.
     */
    fun title(name: String): String {
        val out = StringBuilder(name.length)
        var wordStart = true
        for (c in name) {
            when {
                c == ' ' -> wordStart = true

                wordStart && c.isLetter() -> {
                    out.append(c.uppercaseChar())
                    wordStart = false
                    continue
                }

                wordStart && c.isDigit() -> wordStart = false
            }
            out.append(c)
        }
        return out.toString()
    }

    /** A typed name as it is saved: cleaned, then [title]. */
    fun of(name: String): String = title(NameKey.clean(name))
}
