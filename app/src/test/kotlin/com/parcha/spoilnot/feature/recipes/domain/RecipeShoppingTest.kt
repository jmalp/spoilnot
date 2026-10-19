package com.parcha.spoilnot.feature.recipes.domain

import com.parcha.spoilnot.feature.pantry.domain.PantryFixtures.product
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecipeShoppingTest {
    private fun shop(line: String) = ShoppingName.of(IngredientParser.parse(line).let { it.name ?: it.raw })

    @Test
    fun `size, preparation and freshness words are dropped`() {
        assertEquals("Onion", shop("1 extra large onion, diced"))
        assertEquals("Onions", shop("2 extra-large onions, finely chopped"))
        assertEquals("Eggs", shop("3 large eggs, beaten"))
        assertEquals("Tomatoes", shop("4 ripe tomatoes"))
        assertEquals("Onion", shop("1 onion finely chopped"))
        assertEquals("Chicken breasts", shop("2 boneless skinless chicken breasts"))
        assertEquals("Garlic", shop("3 cloves fresh garlic, minced"))
        assertEquals("Onion", shop("1 medium onion, peeled and diced"))
    }

    @Test
    fun `measures, notes in parentheses and serving phrases are dropped`() {
        assertEquals("Soy sauce", shop("½ tbsp soy sauce"))
        assertEquals("Salt", shop("Salt to taste"))
        assertEquals("Parsley", shop("Fresh parsley, for garnish"))
        assertEquals("Butter", shop("2 tbsp butter or margarine"))
        assertEquals("Olive oil", shop("2 tbsp olive oil, plus more for the pan"))
        assertEquals("Tomatoes", shop("1 (14 ounce) can diced tomatoes"))
    }

    @Test
    fun `words that change what is bought stay`() {
        assertEquals("Ground black pepper", shop("1 tsp freshly ground black pepper"))
        assertEquals("Whole milk", shop("1 cup whole milk"))
        assertEquals("Frozen peas", shop("1 cup frozen peas"))
        assertEquals("Baby spinach", shop("2 cups baby spinach"))
        assertEquals("Hot sauce", shop("1 tsp hot sauce"))
        assertEquals("Salt and pepper", shop("Salt and pepper"))
    }

    @Test
    fun `a name made only of dropped words is kept as written`() {
        assertEquals("Large", ShoppingName.of("large"))
    }

    @Test
    fun `an ingredient links to the product it means, in stock or not`() {
        val onion = product("onion")
        val pepper = product("pepper", "Black pepper")
        val products = listOf(onion, pepper, product("milk", "Whole milk"))
        assertEquals(onion, ProductLink.find("Onion", products))
        assertEquals(onion, ProductLink.find("Onions", products))
        assertEquals(pepper, ProductLink.find("Ground black pepper", products))
        assertNull("a product with more words is something else", ProductLink.find("Milk", products))
    }

    @Test
    fun `no link when it would be a guess`() {
        val products = listOf(product("pepper", "Pepper"), product("green", "Green onions"))
        assertNull("two more words is another product", ProductLink.find("Red bell pepper", products))
        assertNull("green onions are not onions", ProductLink.find("Onion", products))
        assertNull(ProductLink.find("Soy sauce", products))
    }

    @Test
    fun `the product with the most shared words wins`() {
        val pepper = product("pepper", "Pepper")
        val bell = product("bell", "Bell pepper")
        assertEquals(bell, ProductLink.find("Green bell pepper", listOf(pepper, bell)))
    }

    @Test
    fun `a name the household taught a product wins`() {
        val greenOnions = product("green", "Green onions").copy(aliases = listOf("scallion"))
        val products = listOf(product("onion", "Onion"), greenOnions)
        assertEquals(greenOnions, ProductLink.find("Scallions", products))
        assertEquals(greenOnions, ProductLink.find("scallion", products))
    }
}
