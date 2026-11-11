package com.parcha.spoilnot.feature.household.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EmailMaskTest {
    @Test
    fun `keeps two characters of the name part, or one when it is short, and the domain`() {
        assertEquals("ro***@example.com", EmailMask.mask("robin.lee@example.com"))
        assertEquals("ab***@example.com", EmailMask.mask("abc@example.com"))
        assertEquals("a***@example.com", EmailMask.mask("ab@example.com"))
        assertEquals("a***@example.com", EmailMask.mask("a@example.com"))
    }

    @Test
    fun `gives nothing for an address the rules might count differently, or none`() {
        assertNull(EmailMask.mask(null))
        assertNull(EmailMask.mask(""))
        assertNull(EmailMask.mask("no-at-sign"))
        assertNull(EmailMask.mask("a@b@example.com"))
        assertNull(EmailMask.mask("@example.com"))
        assertNull(EmailMask.mask("sam@"))
        assertNull(EmailMask.mask("éva@example.com"))
        assertNull(EmailMask.mask("😀x@example.com"))
    }
}
