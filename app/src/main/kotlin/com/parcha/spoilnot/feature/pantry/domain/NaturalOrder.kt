package com.parcha.spoilnot.feature.pantry.domain

/**
 * Names in the order people expect (after v1): case and accents ignored, and numbers by value, so "Bag 2" comes
 * before "Bag 10" and "Item 20" before "Item 100".
 */
object NaturalOrder : Comparator<String> {
    private val CHUNK = Regex("""\d+|\D+""")

    override fun compare(a: String, b: String): Int {
        val x = CHUNK.findAll(NameKey.of(a)).map { it.value }.toList()
        val y = CHUNK.findAll(NameKey.of(b)).map { it.value }.toList()
        for (i in 0 until minOf(x.size, y.size)) {
            val (p, q) = x[i] to y[i]
            val c = if (p[0].isDigit() && q[0].isDigit()) compareNumbers(p, q) else p.compareTo(q)
            if (c != 0) return c
        }
        return x.size.compareTo(y.size).takeIf { it != 0 } ?: a.compareTo(b)
    }

    /** By value without overflow: fewer digits (after leading zeros) is smaller. */
    private fun compareNumbers(p: String, q: String): Int {
        val a = p.trimStart('0')
        val b = q.trimStart('0')
        return if (a.length != b.length) a.length.compareTo(b.length) else a.compareTo(b)
    }
}
