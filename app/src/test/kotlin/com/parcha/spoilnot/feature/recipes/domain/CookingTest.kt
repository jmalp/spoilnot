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
        val lines = Cooking.lines(r, pantry)
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
        val lines = Cooking.lines(recipe("r1", ingredients = listOf("4 eggs")), pantry, factor = 2.0)
        assertEquals(8.0, lines.single().suggested!!, 0.0)
        assertEquals(6.0, lines.single().defaultAmount, 0.0)
        assertEquals("8 eggs", lines.single().lines.single())
    }
}
