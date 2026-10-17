package com.parcha.spoilnot.feature.recipes.domain

import com.parcha.spoilnot.feature.pantry.domain.PantryData
import com.parcha.spoilnot.feature.pantry.domain.PantryFixtures.batch
import com.parcha.spoilnot.feature.pantry.domain.PantryFixtures.product
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

internal fun recipe(
    id: String,
    title: String = id,
    tags: List<String> = emptyList(),
    favorite: Boolean = false,
    ingredients: List<String> = emptyList(),
    createdSecondsAgo: Long? = 0,
    sourceUrl: String? = null,
    servings: Int? = null,
) = Recipe(
    id = id,
    content = RecipeContent(
        title = title,
        tags = tags,
        ingredients = ingredients.map { IngredientParser.parse(it) },
        sourceUrl = sourceUrl,
        servings = servings,
    ),
    favorite = favorite,
    createdAt = createdSecondsAgo?.let { Instant.parse("2026-10-17T12:00:00Z").minusSeconds(it) },
)

class RecipeBoxTest {
    private val recipes = listOf(
        recipe("a", "Banana Bread", tags = listOf("Baking"), createdSecondsAgo = 300),
        recipe("b", "apple pie", tags = listOf("Baking", "Dessert"), favorite = true, createdSecondsAgo = 200),
        recipe(
            "c",
            "Chili",
            tags = listOf("Dinner"),
            ingredients = listOf("1 can kidney beans"),
            createdSecondsAgo = 100,
        ),
        recipe("d", "Unsynced soup", createdSecondsAgo = null),
    )

    private fun ids(query: RecipeQuery) = RecipeBox.list(recipes, query).map { it.id }

    @Test
    fun `recent first, with recipes not yet on the server at the top`() {
        assertEquals(listOf("d", "c", "b", "a"), ids(RecipeQuery()))
    }

    @Test
    fun `A to Z ignores case`() {
        assertEquals(listOf("b", "a", "c", "d"), ids(RecipeQuery(sort = RecipeSort.AToZ)))
    }

    @Test
    fun `favorites and tag chips`() {
        assertEquals(listOf("b"), ids(RecipeQuery(filter = RecipeFilter.Favorites)))
        assertEquals(listOf("b", "a"), ids(RecipeQuery(filter = RecipeFilter.Tag("baking"))))
    }

    @Test
    fun `search by title, ingredient and tag`() {
        assertEquals(listOf("a"), ids(RecipeQuery(search = "banana")))
        assertEquals(listOf("c"), ids(RecipeQuery(search = "kidney")))
        assertEquals(listOf("b"), ids(RecipeQuery(search = "dessert")))
        assertEquals(emptyList<String>(), ids(RecipeQuery(search = "pizza")))
    }

    @Test
    fun `tags for chips, most used first`() {
        assertEquals(listOf("Baking", "Dessert", "Dinner"), RecipeBox.tags(recipes))
    }

    @Test
    fun `duplicates match the same page`() {
        val saved = listOf(recipe("x", sourceUrl = "https://www.example.com/pie/"))
        assertEquals("x", RecipeBox.duplicateOf("https://example.com/pie?utm_source=share", saved)?.id)
        assertNull(RecipeBox.duplicateOf("https://example.com/cake", saved))
        assertNull(RecipeBox.duplicateOf(null, saved))
    }
}

class PantryMatchTest {
    @Test
    fun `singular forms`() {
        assertEquals("tomato", PantryMatch.singular("tomatoes"))
        assertEquals("berry", PantryMatch.singular("berries"))
        assertEquals("egg", PantryMatch.singular("eggs"))
        assertEquals("peach", PantryMatch.singular("peaches"))
        assertEquals("hummus", PantryMatch.singular("hummus"))
        assertEquals("glass", PantryMatch.singular("glass"))
    }

    @Test
    fun `every product word, singularized, must be in the line`() {
        assertTrue(PantryMatch.matches("egg", "3 large eggs, beaten"))
        assertTrue(PantryMatch.matches("olive oil", "2 tbsp extra-virgin olive oil"))
        assertTrue(PantryMatch.matches("tomatoes", "1 tomato"))
        assertFalse(PantryMatch.matches("olive oil", "2 tbsp vegetable oil"))
        assertFalse(PantryMatch.matches("milk", "1 cup buttermilk"))
    }

    @Test
    fun `only products in stock, the most specific first`() {
        val pantry = PantryData(
            products = listOf(product("oil", "Oil"), product("olive", "Olive oil"), product("milk", "Milk")),
            batches = listOf(batch("b1", "oil"), batch("b2", "olive"), batch("b3", "milk", quantity = 0.0)),
            fromCache = false,
        )
        assertEquals("olive", PantryMatch.productFor("3 tbsp olive oil", pantry)?.id)
        assertNull(PantryMatch.productFor("1 cup milk", pantry))
    }
}

class RecipeLinesTest {
    @Test
    fun `lines ending in a colon start a group, and round trip`() {
        val text = "For the dough:\n2 cups flour\n1 tsp salt\n\nFor the filling:\n3 apples"
        val ingredients = RecipeLines.ingredients(text)
        assertEquals(listOf("For the dough", "For the dough", "For the filling"), ingredients.map { it.group })
        assertEquals(2.0, ingredients[0].qty!!, 1e-9)
        assertEquals(text.replace("\n\n", "\n"), RecipeLines.ingredientsToText(ingredients))
        assertEquals(listOf(Step("Mix.", null), Step("Bake.", null)), RecipeLines.steps("Mix.\n  \nBake."))
    }

    @Test
    fun `tags from a comma-separated field`() {
        assertEquals(listOf("Quick", "vegan"), RecipeLines.tags(" Quick, vegan ,, Vegan"))
    }
}

class RecipeFormTest {
    @Test
    fun `problems with the title, image link and numbers`() {
        assertEquals(setOf(RecipeForm.Problem.TitleMissing), RecipeForm().problems())
        assertEquals(
            setOf(RecipeForm.Problem.ImageUrl, RecipeForm.Problem.Number),
            RecipeForm(title = "Pie", imageUrl = "file:///x.jpg", servings = "four").problems(),
        )
        assertEquals(emptySet<RecipeForm.Problem>(), RecipeForm(title = "Pie", servings = "4").problems())
    }

    @Test
    fun `total is prep plus cook when left blank, and hidden fields are kept`() {
        val base = RecipeContent(title = "Old", sourceUrl = "https://example.com/pie", importMethod = ImportMethod.Web)
        val c = RecipeForm(
            title = " Pie ",
            prepMinutes = "10",
            cookMinutes = "35",
            ingredients = "1 cup sugar",
        ).toContent(base)
        assertEquals("Pie", c.title)
        assertEquals(45, c.totalMinutes)
        assertEquals("https://example.com/pie", c.sourceUrl)
        assertEquals(ImportMethod.Web, c.importMethod)
        assertEquals("cup", c.ingredients.single().unit)
    }

    @Test
    fun `a form made from content gives the same content back`() {
        val content = RecipeContent(
            title = "Curry",
            servings = 4,
            servingsText = "4 servings",
            totalMinutes = 40,
            ingredients = RecipeLines.ingredients("Sauce:\n1 cup coconut milk"),
            steps = RecipeLines.steps("Cook:\nSimmer."),
            notes = "Spicy",
            tags = listOf("Dinner", "Thai"),
        )
        assertEquals(content, RecipeForm.from(content).toContent(content))
    }
}
