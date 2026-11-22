package com.parcha.spoilnot.feature.pantry.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/** A food's name is saved with every word capitalized (DESIGN 8.1; owner, 2026-11-21). The web's tests match. */
class NameCaseTest {
    @Test
    fun `every word starts with a capital, whatever was typed`() {
        assertEquals("Black Beans", NameCase.of("black beans"))
        assertEquals("Black Beans", NameCase.of("Black beans"))
        assertEquals("Super Pickled Pickles Of Doom", NameCase.of("super Pickled pickles of Doom"))
    }

    @Test
    fun `the rest of each word stays as typed`() {
        assertEquals("BBQ Sauce", NameCase.of("BBQ sauce"))
        assertEquals("M&Ms", NameCase.of("m&Ms"))
        assertEquals("O'brien Potatoes", NameCase.of("o'brien potatoes"))
    }

    @Test
    fun `it is cleaned first`() {
        assertEquals("Greek Yogurt", NameCase.of("  greek   yogurt "))
    }

    @Test
    fun `punctuation before a word's first letter keeps the word open, a digit doesn't`() {
        assertEquals("Eggs (Organic)", NameCase.of("eggs (organic)"))
        assertEquals("\"Fancy\" Ham", NameCase.of("\"fancy\" ham"))
        assertEquals("2% Milk", NameCase.of("2% milk"))
        assertEquals("7up", NameCase.of("7up"))
        assertEquals("Rice 2kg", NameCase.of("rice 2kg"))
    }

    @Test
    fun `letters beyond English, and one with no single capital`() {
        assertEquals("Élan Crème Fraîche", NameCase.of("élan crème fraîche"))
        assertEquals("Ñame", NameCase.of("ñame"))
        assertEquals("ßtraße", NameCase.of("ßtraße"))
    }

    @Test
    fun `matching doesn't change`() {
        assertEquals(NameKey.of("black beans"), NameKey.of(NameCase.of("black beans")))
    }
}
