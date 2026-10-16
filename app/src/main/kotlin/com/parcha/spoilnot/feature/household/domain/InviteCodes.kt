package com.parcha.spoilnot.feature.household.domain

import java.security.SecureRandom
import java.util.Random

/** Household invite codes (DESIGN 5.1, 12.2): 10 characters, about 50 bits, no look-alike characters. */
object InviteCodes {
    /** No I, O, 0 or 1. */
    const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    const val LENGTH = 10
    private const val GROUP = 5

    private val secureRandom = SecureRandom()

    fun generate(random: Random = secureRandom): String =
        buildString(LENGTH) { repeat(LENGTH) { append(ALPHABET[random.nextInt(ALPHABET.length)]) } }

    /** Uppercases and drops separators, so pasted "abcde-23456" and typed input end up the same. */
    fun normalize(input: String): String = input.uppercase().filter { it.isLetterOrDigit() }.take(LENGTH)

    fun isWellFormed(code: String): Boolean = code.length == LENGTH && code.all { it in ALPHABET }

    /** For display: a hyphen after the fifth character. */
    fun format(code: String): String = if (code.length > GROUP) code.take(GROUP) + "-" + code.drop(GROUP) else code
}
