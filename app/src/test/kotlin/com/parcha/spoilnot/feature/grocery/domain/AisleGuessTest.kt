package com.parcha.spoilnot.feature.grocery.domain

import com.parcha.spoilnot.feature.pantry.domain.Category
import com.parcha.spoilnot.feature.pantry.domain.PantryFixtures.product
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AisleGuessTest {
    @Test
    fun `common names get their aisle, plurals included`() {
        assertEquals(Category.Produce, AisleGuess.of("Bananas"))
        assertEquals(Category.Produce, AisleGuess.of("cherry tomatoes"))
        assertEquals(Category.Dairy, AisleGuess.of("Greek yogurt"))
        assertEquals(Category.MeatFish, AisleGuess.of("chicken thighs"))
        assertEquals(Category.Bakery, AisleGuess.of("Sourdough bread"))
        assertEquals(Category.Drinks, AisleGuess.of("sparkling water"))
        assertEquals(Category.Snacks, AisleGuess.of("tortilla chips"))
    }

    @Test
    fun `a longer phrase wins over the words in it`() {
        assertEquals(Category.PantryStaples, AisleGuess.of("crunchy peanut butter"))
        assertEquals(Category.Frozen, AisleGuess.of("frozen peas"))
        assertEquals(Category.Frozen, AisleGuess.of("vanilla ice cream"))
        assertEquals(Category.PantryStaples, AisleGuess.of("coconut milk"))
    }

    @Test
    fun `the household's own products come first`() {
        // This household files oat milk under Drinks, not Dairy.
        val oatMilk = product("oatmilk", "Oat milk", category = Category.Drinks)
        assertEquals(Category.Drinks, AisleGuess.of("oat milk, barista", listOf(oatMilk)))
        assertEquals(Category.Dairy, AisleGuess.of("milk", listOf(oatMilk)))
    }

    @Test
    fun `unknown names stay without an aisle`() {
        assertNull(AisleGuess.of("batteries"))
        assertNull(AisleGuess.of("  "))
        // Whole words only: "pea" is not in "peanut", "ham" is not in "hammer".
        assertNull(AisleGuess.of("hammer"))
    }
}
