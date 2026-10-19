package com.parcha.spoilnot.feature.recipes.domain

import com.parcha.spoilnot.core.error.AppError
import com.parcha.spoilnot.core.write.Actor
import com.parcha.spoilnot.core.write.HouseholdChange
import com.parcha.spoilnot.core.write.HouseholdWriter
import com.parcha.spoilnot.feature.grocery.domain.GroceryChange
import com.parcha.spoilnot.feature.grocery.domain.GroceryItem
import com.parcha.spoilnot.feature.grocery.domain.ItemSource
import com.parcha.spoilnot.feature.pantry.domain.Category
import com.parcha.spoilnot.feature.pantry.domain.NameKey
import com.parcha.spoilnot.feature.pantry.domain.PantryData
import com.parcha.spoilnot.feature.pantry.domain.PantryFixtures.batch
import com.parcha.spoilnot.feature.pantry.domain.PantryFixtures.product
import com.parcha.spoilnot.feature.pantry.domain.QuantityUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecipeServiceTest {
    private val writer = RecordingWriter()
    private val service = RecipeService(writer)
    private val actor = Actor("u1", "Sam")

    @Test
    fun `save creates the recipe and logs recipe_saved in one write`() = runTest {
        val result = service.save(
            "h1",
            actor,
            RecipeContent(title = "  Pie ", sourceUrl = "https://example.com/pie"),
            emptyList(),
        )
        assertEquals(SaveResult.Saved("id0"), result)
        val changes = writer.applied.single()
        val create = changes[0] as RecipeChange.Create
        assertEquals("Pie", create.recipe.title)
        assertEquals("u1", create.recipe.createdBy)
        assertEquals(RecipeChange.LogSaved("id1", "id0", "Pie"), changes[1])
    }

    @Test
    fun `a link already in the box asks first, unless saving a copy`() = runTest {
        val existing = listOf(recipe("r1", sourceUrl = "https://www.example.com/pie"))
        val content = RecipeContent(title = "Pie", sourceUrl = "https://example.com/pie/?utm_source=x")
        assertEquals(SaveResult.AlreadySaved(existing[0]), service.save("h1", actor, content, existing))
        assertTrue(writer.applied.isEmpty())
        assertTrue(service.save("h1", actor, content, existing, saveCopy = true) is SaveResult.Saved)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a title is required`() = runTest {
        service.save("h1", actor, RecipeContent(title = "  "), emptyList())
    }

    @Test
    fun `validation drops bad links and caps tags`() {
        val c = service.validated(
            RecipeContent(
                title = "Pie",
                imageUrl = "javascript:alert(1)",
                sourceUrl = "ftp://example.com/pie",
                servings = 0,
                tags = (1..10).map { "t$it" },
            ),
        )
        assertNull(c.imageUrl)
        assertNull(c.sourceUrl)
        assertNull(c.servings)
        assertEquals(8, c.tags.size)
    }

    @Test
    fun `delete can be undone with the same recipe`() = runTest {
        val r = recipe("r1", "Pie", favorite = true)
        val undo = service.delete("h1", actor, r)
        assertEquals(listOf(RecipeChange.Delete("r1")), writer.applied.single())
        assertEquals(listOf(RecipeChange.Restore(r)), undo.changes)
    }

    @Test
    fun `grocery candidates have shopping names, pantry hints, items already on the list and the recipe as a note`() {
        val r = recipe(
            "r1",
            title = "Pancakes",
            ingredients = listOf("3 large eggs", "2 tbsp olive oil", "500 g flour", "2 cans tomatoes", "Salt to taste"),
        )
        val pantry = PantryData(
            products = listOf(product("egg", "Eggs"), product("oil", "Olive oil", category = Category.PantryStaples)),
            batches = listOf(batch("b1", "egg", quantity = 6.0), batch("b2", "oil")),
            fromCache = false,
        )
        val onList = listOf(groceryItem("Salt"), groceryItem("tomatoes", checked = true))
        val c = RecipeGrocery.candidates(r, pantry, onList)
        assertEquals(listOf("Eggs", "Olive oil", "Flour", "Tomatoes", "Salt"), c.map { it.name })
        assertEquals(listOf("egg", "oil", null, null, null), c.map { it.product?.id })
        assertEquals("egg", c[0].inPantry?.id)
        assertEquals(3.0, c[0].quantity!!, 1e-9)
        // Measures are not shopping amounts: they stay in the note.
        assertNull(c[1].quantity)
        assertNull(c[2].quantity)
        assertNull(c[2].unit)
        assertEquals("Pancakes: 2 tbsp olive oil", c[1].note)
        assertEquals(2.0, c[3].quantity!!, 1e-9)
        // Checked items are in the cart, not "on the list".
        assertEquals(listOf(false, false, true, true, false), c.map { it.selectedByDefault })
    }

    @Test
    fun `a linked product brings its name and usual amount`() {
        val onions = product("onion", "Onions", unit = QuantityUnit.Pounds).copy(lastAddedQuantity = 3.0)
        val soy = product("soy", "Soy sauce").copy(buyQuantity = 1.0, lastAddedQuantity = 2.0)
        val r = recipe("r1", "Stir fry", ingredients = listOf("1 extra large onion, diced", "½ tbsp soy sauce"))
        val c = RecipeGrocery.candidates(r, PantryData(listOf(onions, soy), emptyList(), false), emptyList())
        assertEquals(listOf("Onions", "Soy sauce"), c.map { it.name })
        assertEquals(listOf(3.0, 1.0), c.map { it.quantity }) // Buy quantity wins over the last amount
        assertEquals(listOf(QuantityUnit.Pounds, QuantityUnit.Pieces), c.map { it.unit })
        assertEquals("Stir fry: 1 extra large onion, diced", c[0].note)
        // Not in stock, so not "probably in pantry", but still linked.
        assertTrue(c.all { it.inPantry == null && it.selectedByDefault })
    }

    @Test
    fun `an item already on the list for the same product is not added twice`() {
        val onion = product("onion")
        val listed = groceryItem("onions").copy(productId = "onion")
        val r = recipe("r1", ingredients = listOf("1 large onion"))
        val c = RecipeGrocery.candidates(r, PantryData(listOf(onion), emptyList(), false), listOf(listed))
        assertTrue(c.single().onList)
    }

    @Test
    fun `added items carry the source Recipe and link pantry products`() = runTest {
        val r = recipe("r1", ingredients = listOf("2 tbsp olive oil", "1 cup rice"))
        val pantry =
            PantryData(listOf(product("oil", "Olive oil", category = Category.PantryStaples)), emptyList(), false)
        val chosen = RecipeGrocery.candidates(r, pantry, emptyList())
        assertEquals(2, service.addToGrocery("h1", actor, "list1", r, chosen, pantry, "Groceries"))
        val changes = writer.applied.single()
        val items = changes.filterIsInstance<GroceryChange.CreateItem>().map { it.item }
        // One activity event for the whole add: "added 2 ingredients from r1".
        val event = changes.last() as GroceryChange.LogEvent
        assertEquals(Triple("r1", "Groceries", 2), Triple(event.name, event.listName, event.count))
        assertEquals(listOf("Olive oil", "Rice"), items.map { it.name })
        assertTrue(items.all { it.source == ItemSource.Recipe && it.recipeId == "r1" })
        assertEquals("oil", items[0].productId)
        assertEquals(Category.PantryStaples, items[0].category)
        assertNull(items[1].productId)
    }

    @Test
    fun `lines for the same thing become one item`() = runTest {
        val r = recipe("r1", "Salsa", ingredients = listOf("1 onion, diced", "2 large onions, sliced", "1 tsp salt"))
        val chosen = RecipeGrocery.candidates(r, PantryData(emptyList(), emptyList(), false), emptyList())
        service.addToGrocery("h1", actor, "list1", r, chosen, PantryData(emptyList(), emptyList(), false))
        val items = writer.applied.single().filterIsInstance<GroceryChange.CreateItem>().map { it.item }
        // "Onion" and "Onions" are different names; two onion lines with one name merge.
        assertEquals(listOf("Onion", "Onions", "Salt"), items.map { it.name })
        val merged = RecipeGrocery.merge(
            RecipeGrocery.candidates(
                recipe("r2", "Soup", ingredients = listOf("1 onion, diced", "1 onion, sliced")),
                PantryData(emptyList(), emptyList(), false),
                emptyList(),
            ),
        ).single()
        assertEquals(2.0, merged.quantity!!, 1e-9)
        assertEquals("Soup: 1 onion, diced; Soup: 1 onion, sliced", merged.note)
    }

    private fun groceryItem(name: String, checked: Boolean = false) = GroceryItem(
        id = name,
        name = name,
        nameKey = NameKey.of(name),
        quantity = null,
        unit = null,
        note = null,
        category = null,
        checked = checked,
        checkedBy = null,
        source = ItemSource.Manual,
        productId = null,
        recipeId = null,
        createdBy = null,
        createdAt = null,
    )

    private class RecordingWriter : HouseholdWriter {
        val applied = mutableListOf<List<HouseholdChange>>()
        private var next = 0

        override fun newId() = "id${next++}"

        override suspend fun apply(householdId: String, actor: Actor, changes: List<HouseholdChange>) {
            applied += changes
        }

        override val errors: Flow<AppError> = emptyFlow()
    }
}
