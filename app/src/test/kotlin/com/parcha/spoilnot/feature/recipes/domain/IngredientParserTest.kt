package com.parcha.spoilnot.feature.recipes.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IngredientParserTest {
    private fun parsed(raw: String) = IngredientParser.parse(raw)

    @Test
    fun `integers, decimals and units`() {
        assertEquals(Ingredient("2 cups flour", 2.0, "cup", "flour"), parsed("2 cups flour"))
        assertEquals(Ingredient("1.5 kg potatoes", 1.5, "kg", "potatoes"), parsed("1.5 kg potatoes"))
        assertEquals(Ingredient(".5 tsp salt", 0.5, "tsp", "salt"), parsed(".5 tsp salt"))
    }

    @Test
    fun `unicode fractions and mixed numbers`() {
        assertEquals(0.5, parsed("½ cup sugar").qty!!, 1e-9)
        assertEquals(1.5, parsed("1½ cups milk").qty!!, 1e-9)
        assertEquals(1.5, parsed("1 ½ cups milk").qty!!, 1e-9)
        assertEquals(1.5, parsed("1 1/2 cups milk").qty!!, 1e-9)
        assertEquals(0.75, parsed("3/4 tsp vanilla").qty!!, 1e-9)
        assertEquals(0.5, parsed("1⁄2 tsp salt").qty!!, 1e-9)
        assertEquals("cup", parsed("1 1/2 cups milk").unit)
    }

    @Test
    fun `ranges use the lower value`() {
        assertEquals(2.0, parsed("2-3 cloves garlic").qty!!, 1e-9)
        assertEquals(2.0, parsed("2 – 3 cloves garlic").qty!!, 1e-9)
        assertEquals(2.0, parsed("2 to 3 tbsp oil").qty!!, 1e-9)
        assertEquals("clove", parsed("2-3 cloves garlic").unit)
        assertEquals(3.0, IngredientParser.quantity("2-3 cloves garlic")!!.max!!, 1e-9)
    }

    @Test
    fun `plurals, abbreviations and units glued to the number`() {
        assertEquals("tbsp", parsed("2 Tbsp. olive oil").unit)
        assertEquals("olive oil", parsed("2 Tbsp. olive oil").name)
        assertEquals("g", parsed("200g flour").unit)
        assertEquals("lb", parsed("2 lbs chicken").unit)
        assertEquals("oz", parsed("8 oz. cream cheese").unit)
        assertEquals("package", parsed("1 package yeast").unit)
        assertEquals("pinch", parsed("1 pinch nutmeg").unit)
        assertEquals("l", parsed("1 l water").unit)
        assertEquals("ml", parsed("250 ml milk").unit)
    }

    @Test
    fun `a word that starts like a unit is part of the name`() {
        assertEquals(Ingredient("2 garlic cloves", 2.0, null, "garlic cloves"), parsed("2 garlic cloves"))
        assertEquals(Ingredient("3 large eggs", 3.0, null, "large eggs"), parsed("3 large eggs"))
        assertEquals(Ingredient("2 carrots", 2.0, null, "carrots"), parsed("2 carrots"))
        assertEquals(Ingredient("1 tomato", 1.0, null, "tomato"), parsed("1 tomato"))
    }

    @Test
    fun `the name drops text after the first comma and anything in parentheses`() {
        assertEquals("onion", parsed("1 onion, finely chopped").name)
        assertEquals("butter", parsed("2 tbsp butter (softened)").name)
        assertEquals("flour", parsed("1 cup of flour").name)
        // The can size sits between the quantity and the unit.
        val tomatoes = parsed("1 (14 ounce) can diced tomatoes, drained")
        assertEquals(1.0, tomatoes.qty!!, 1e-9)
        assertEquals("can", tomatoes.unit)
        assertEquals("diced tomatoes", tomatoes.name)
    }

    @Test
    fun `lines without a leading quantity keep only raw`() {
        assertEquals(Ingredient("Salt and pepper to taste"), parsed("Salt and pepper to taste"))
        assertEquals(Ingredient("A handful of basil"), parsed("A handful of basil"))
        assertNull(parsed("0 eggs").qty)
    }

    @Test
    fun `the group is kept`() {
        assertEquals("Sauce", IngredientParser.parse("1 cup stock", "Sauce").group)
    }
}

class ScalingTest {
    @Test
    fun `scales only the quantity and keeps the rest of the line`() {
        assertEquals("3 cups flour, sifted", Scaling.line("1 ½ cups flour, sifted", 2.0))
        assertEquals("400g flour", Scaling.line("200g flour", 2.0))
        assertEquals("1–1½ cloves garlic", Scaling.line("2-3 cloves garlic", 0.5))
        assertEquals("Salt to taste", Scaling.line("Salt to taste", 3.0))
        assertEquals("2 cups milk", Scaling.line("2 cups milk", 1.0))
    }

    @Test
    fun `factor comes from the original and target servings`() {
        assertEquals(1.5, Scaling.factor(4, 6), 1e-9)
        assertEquals(1.0, Scaling.factor(null, 6), 1e-9)
        assertEquals(1.0, Scaling.factor(4, null), 1e-9)
    }

    @Test
    fun `quantities are shown as kitchen fractions`() {
        assertEquals("½", Scaling.formatQuantity(0.5))
        assertEquals("⅓", Scaling.formatQuantity(1.0 / 3))
        assertEquals("1¾", Scaling.formatQuantity(1.75))
        assertEquals("2", Scaling.formatQuantity(1.99))
        assertEquals("12", Scaling.formatQuantity(12.4))
        assertEquals("1.1", Scaling.formatQuantity(1.1))
        assertEquals("⅛", Scaling.formatQuantity(0.125))
    }
}
