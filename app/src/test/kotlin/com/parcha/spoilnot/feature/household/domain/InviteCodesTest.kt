package com.parcha.spoilnot.feature.household.domain

import java.util.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InviteCodesTest {
    @Test
    fun `generated codes are 10 characters from the safe alphabet`() {
        repeat(1_000) {
            val code = InviteCodes.generate()
            assertEquals(10, code.length)
            assertTrue(code, InviteCodes.isWellFormed(code))
        }
    }

    @Test
    fun `the alphabet has no look-alike characters`() {
        listOf('I', 'O', '0', '1').forEach { assertFalse(it in InviteCodes.ALPHABET) }
        assertEquals(32, InviteCodes.ALPHABET.length)
    }

    @Test
    fun `generation uses the given random source`() {
        assertEquals(InviteCodes.generate(Random(42)), InviteCodes.generate(Random(42)))
        assertNotEquals(InviteCodes.generate(Random(1)), InviteCodes.generate(Random(2)))
    }

    @Test
    fun `codes cover the whole alphabet`() {
        val seen = (1..2_000).flatMap { InviteCodes.generate().toList() }.toSet()
        assertEquals(InviteCodes.ALPHABET.toSet(), seen)
    }

    @Test
    fun `normalize uppercases and drops separators and extra characters`() {
        assertEquals("ABCDE23456", InviteCodes.normalize("abcde-23456"))
        assertEquals("ABCDE23456", InviteCodes.normalize(" ABCDE 23456 "))
        assertEquals("ABCDE23456", InviteCodes.normalize("ABCDE-23456-XYZ"))
        assertEquals("ABC", InviteCodes.normalize("a-b-c"))
    }

    @Test
    fun `well-formed means exact length and only safe characters`() {
        assertTrue(InviteCodes.isWellFormed("ABCDE23456"))
        assertFalse(InviteCodes.isWellFormed("ABCDE2345"))
        assertFalse(InviteCodes.isWellFormed("ABCDE2345O"))
        assertFalse(InviteCodes.isWellFormed("abcde23456"))
    }

    @Test
    fun `format adds a hyphen after five characters`() {
        assertEquals("ABCDE-23456", InviteCodes.format("ABCDE23456"))
        assertEquals("ABC", InviteCodes.format("ABC"))
        assertEquals("ABCDE-2", InviteCodes.format("ABCDE2"))
    }
}
