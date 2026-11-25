package com.parcha.spoilnot.core.write

/**
 * The longest text the screens take from Firestore (DESIGN 8.1). The rules limit what is written, but data written
 * before they did can be longer, and a very long name would slow every list that shows it.
 */
object ReadCaps {
    /** Products, batches and grocery items. */
    const val NAME = 80

    /** Display names of people. */
    const val PERSON = 40

    /** Households and grocery lists. */
    const val LIST_NAME = 60

    const val RECIPE_TITLE = 200
    const val MEAL_TITLE = 120
}

/** This text cut to [max] characters, without splitting a character made of two UTF-16 units. */
fun String.capTo(max: Int): String {
    if (length <= max) return this
    val end = if (max > 0 && this[max - 1].isHighSurrogate()) max - 1 else max
    return substring(0, end)
}
