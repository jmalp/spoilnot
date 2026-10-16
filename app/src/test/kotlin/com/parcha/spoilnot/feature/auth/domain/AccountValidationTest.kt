package com.parcha.spoilnot.feature.auth.domain

import com.parcha.spoilnot.feature.auth.domain.AccountValidation.NameProblem
import com.parcha.spoilnot.feature.household.domain.HouseholdRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountValidationTest {
    @Test
    fun `display names are 1 to 40 characters after trimming`() {
        assertEquals(NameProblem.Blank, AccountValidation.displayNameProblem("   "))
        assertNull(AccountValidation.displayNameProblem(" Sam "))
        assertNull(AccountValidation.displayNameProblem("x".repeat(40)))
        assertEquals(NameProblem.TooLong, AccountValidation.displayNameProblem("x".repeat(41)))
    }

    @Test
    fun `passwords need at least 8 characters`() {
        assertFalse(AccountValidation.isPasswordLongEnough("1234567"))
        assertTrue(AccountValidation.isPasswordLongEnough("12345678"))
    }

    @Test
    fun `email check accepts normal addresses and rejects obvious mistakes`() {
        assertTrue(AccountValidation.isEmailValid("sam@example.com"))
        assertTrue(AccountValidation.isEmailValid(" sam.smith+food@mail.example.org "))
        assertFalse(AccountValidation.isEmailValid("sam@example"))
        assertFalse(AccountValidation.isEmailValid("sam example.com"))
        assertFalse(AccountValidation.isEmailValid(""))
    }

    @Test
    fun `fallback display name comes from the email`() {
        assertEquals("sam", AccountValidation.fallbackDisplayName("sam@example.com"))
        assertEquals("Me", AccountValidation.fallbackDisplayName(null))
        assertEquals("Me", AccountValidation.fallbackDisplayName("@example.com"))
    }

    @Test
    fun `household names are 1 to 60 characters`() {
        assertFalse(HouseholdRules.isNameValid(" "))
        assertTrue(HouseholdRules.isNameValid("The Smiths"))
        assertTrue(HouseholdRules.isNameValid("x".repeat(60)))
        assertFalse(HouseholdRules.isNameValid("x".repeat(61)))
    }
}
