package com.parcha.spoilnot.core.write

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ReadCapsTest {
    @Test
    fun `text within the cap is kept as it is`() {
        val name = "Milk"
        assertSame(name, name.capTo(ReadCaps.NAME))
        assertEquals("x".repeat(80), "x".repeat(80).capTo(ReadCaps.NAME))
    }

    @Test
    fun `longer text is cut to the cap`() {
        assertEquals("x".repeat(80), "x".repeat(500_000).capTo(ReadCaps.NAME))
        assertEquals("Sam", "Samantha".capTo(3))
    }

    @Test
    fun `a character made of two units is not split`() {
        // "🥛" is two UTF-16 units; a cut between them would leave half a character.
        assertEquals("ab", "ab🥛".capTo(3))
        assertEquals("ab🥛", "ab🥛c".capTo(4))
    }
}
