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

    /** After v1 (UX-09): Add item gives a new product this aisle, Other when nothing matches. */
    @Test
    fun `a new product takes the guess, or Other`() {
        assertEquals(Category.Dairy, AisleGuess.forNewProduct("Greek yogurt", emptyList()))
        assertEquals(Category.Other, AisleGuess.forNewProduct("Dish soap", emptyList()))
        assertEquals(Category.Other, AisleGuess.forNewProduct("", emptyList()))
        val household = listOf(product("oat", name = "Oat milk", category = Category.Drinks))
        assertEquals(Category.Drinks, AisleGuess.forNewProduct("oat milk, barista", household))
    }

    @Test
    fun `a longer phrase wins over the words in it`() {
        assertEquals(Category.PantryStaples, AisleGuess.of("crunchy peanut butter"))
        assertEquals(Category.Frozen, AisleGuess.of("frozen peas"))
        assertEquals(Category.Frozen, AisleGuess.of("vanilla ice cream"))
        assertEquals(Category.PantryStaples, AisleGuess.of("coconut milk"))
    }

    @Test
    fun `how it is kept and what kind of product it is win over the food word`() {
        // After v1, LOGIC-17: "chicken" is longer than "broth", but chicken broth is a pantry staple.
        assertEquals(Category.PantryStaples, AisleGuess.of("chicken broth"))
        assertEquals(Category.PantryStaples, AisleGuess.of("beef stock"))
        assertEquals(Category.PantryStaples, AisleGuess.of("tomato paste"))
        assertEquals(Category.PantryStaples, AisleGuess.of("salad dressing"))
        assertEquals(Category.PantryStaples, AisleGuess.of("canned tomatoes"))
        assertEquals(Category.Drinks, AisleGuess.of("apple juice"))
        assertEquals(Category.Frozen, AisleGuess.of("frozen spinach"))
        assertEquals(Category.Frozen, AisleGuess.of("frozen chicken"))
        // "Frozen" wins over a phrase without it, too.
        assertEquals(Category.Frozen, AisleGuess.of("frozen ground beef"))
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
