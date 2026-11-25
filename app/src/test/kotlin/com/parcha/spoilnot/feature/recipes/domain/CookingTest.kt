package com.parcha.spoilnot.feature.recipes.domain

import com.parcha.spoilnot.feature.pantry.domain.PantryData
import com.parcha.spoilnot.feature.pantry.domain.PantryFixtures.batch
import com.parcha.spoilnot.feature.pantry.domain.PantryFixtures.product
import com.parcha.spoilnot.feature.pantry.domain.QuantityUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CookingTest {
    private val eggs = product("egg", "Eggs")
    private val rice = product("rice", "Rice", unit = QuantityUnit.Grams)
    private val soy = product("soy", "Soy sauce")
    private val milk = product("milk", "Milk")
    private val pantry = PantryData(
        listOf(eggs, rice, soy, milk),
        listOf(
            batch("e1", "egg", quantity = 4.0),
            batch("e2", "egg", quantity = 2.0),
            batch("r1", "rice", quantity = 900.0),
            batch("s1", "soy", quantity = 1.0),
        ),
        fromCache = false,
    )

    @Test
    fun `counts and same-unit amounts are suggested, measures are not`() {
        val r = recipe(
            "r1",
            ingredients = listOf("3 large eggs", "500 g rice", "2 tbsp soy sauce", "1 cup milk", "1 egg, for brushing"),
        )
        val lines = Cooking.lines(r, pantry, TEST_WORDS)
        assertEquals(listOf("egg", "rice", "soy"), lines.map { it.product.id }) // milk is out of stock
        assertEquals(4.0, lines[0].suggested!!, 0.0) // 3 + 1 eggs
        assertEquals(listOf("3 large eggs", "1 egg, for brushing"), lines[0].lines)
        assertEquals(6.0, lines[0].inStock, 0.0)
        assertEquals(500.0, lines[1].suggested!!, 0.0)
        assertNull(lines[2].suggested)
        assertTrue(lines[0].selectedByDefault)
        assertFalse(lines[2].selectedByDefault)
        assertEquals("finishing it is the default amount", 1.0, lines[2].defaultAmount, 0.0)
    }

    @Test
    fun `servings scale the amounts, capped at what is in stock`() {
        val lines = Cooking.lines(recipe("r1", ingredients = listOf("4 eggs")), pantry, TEST_WORDS, factor = 2.0)
        assertEquals(8.0, lines.single().suggested!!, 0.0)
        assertEquals(6.0, lines.single().defaultAmount, 0.0)
        assertEquals("8 eggs", lines.single().lines.single())
    }

    @Test
    fun `scaled pieces are suggested whole, rounded up, and the recipe's own amount as written`() {
        // After v1, LOGIC-13: 1.5 times "3 eggs" takes 5, not 4.5, like the grocery list.
        val omelette = recipe("r1", ingredients = listOf("3 eggs"))
        assertEquals(5.0, Cooking.lines(omelette, pantry, TEST_WORDS, factor = 1.5).single().suggested!!, 0.0)
        val half = recipe("r2", ingredients = listOf("½ egg"))
        assertEquals(0.5, Cooking.lines(half, pantry, TEST_WORDS).single().suggested!!, 0.0)
        // Grams are not pieces: 1.5 times 333 g is 499.5 g.
        val rice = recipe("r3", ingredients = listOf("333 g rice"))
        assertEquals(499.5, Cooking.lines(rice, pantry, TEST_WORDS, factor = 1.5).single().suggested!!, 1e-9)
    }

    @Test
    fun `a tiny scaled amount reads a pinch, not 0`() {
        val salt = product("salt", "Salt", unit = QuantityUnit.Grams)
        val withSalt = PantryData(listOf(salt), listOf(batch("s", "salt", quantity = 100.0)), fromCache = false)
        val line = Cooking.lines(recipe("r1", ingredients = listOf("1/8 tsp salt")), withSalt, TEST_WORDS, 0.1).single()
        assertEquals(listOf("a pinch of salt"), line.lines)
    }
}
