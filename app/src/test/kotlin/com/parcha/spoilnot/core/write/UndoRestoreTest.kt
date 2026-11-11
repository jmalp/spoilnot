package com.parcha.spoilnot.core.write

import com.parcha.spoilnot.core.firebase.FakeServer
import com.parcha.spoilnot.feature.grocery.data.FirestoreGroceryRepository.Companion.toItem
import com.parcha.spoilnot.feature.grocery.data.GroceryChangeMapper
import com.parcha.spoilnot.feature.grocery.domain.GroceryChange
import com.parcha.spoilnot.feature.grocery.domain.GroceryItem
import com.parcha.spoilnot.feature.grocery.domain.GroceryItemFields
import com.parcha.spoilnot.feature.grocery.domain.ItemSource
import com.parcha.spoilnot.feature.pantry.domain.Category
import com.parcha.spoilnot.feature.pantry.domain.QuantityUnit
import com.parcha.spoilnot.feature.plan.data.MealPlanChangeMapper
import com.parcha.spoilnot.feature.plan.domain.MealPlanChange
import com.parcha.spoilnot.feature.plan.domain.MealSlot
import com.parcha.spoilnot.feature.plan.domain.PlannedMeal
import com.parcha.spoilnot.feature.recipes.data.RecipeChangeMapper
import com.parcha.spoilnot.feature.recipes.domain.Recipe
import com.parcha.spoilnot.feature.recipes.domain.RecipeChange
import com.parcha.spoilnot.feature.recipes.domain.RecipeContent
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Undo of a deleted grocery item, recipe or meal (after v1, DESIGN 8.6): it removes the tombstone and nothing else, so
 * the document keeps fields a newer app added, its creator, a check-off and another member's change. A grocery edit
 * writes only the fields that changed.
 */
class UndoRestoreTest {
    private val server = FakeServer()
    private val alice = Actor("alice", "Alice")
    private val bob = Actor("bob", "Bob")
    private val restoreFields = setOf("deletedAt", "updatedAt", "updatedBy")

    private fun FirestoreChangeMapperLike.run(actor: Actor, change: HouseholdChange) =
        assertTrue(write(server, actor, change))

    private fun interface FirestoreChangeMapperLike {
        fun write(server: FakeServer, actor: Actor, change: HouseholdChange): Boolean
    }

    private val grocery = FirestoreChangeMapperLike { s, a, c -> GroceryChangeMapper().write(s.batch, s.db, "h1", a, c) }
    private val recipes = FirestoreChangeMapperLike { s, a, c -> RecipeChangeMapper().write(s.batch, s.db, "h1", a, c) }
    private val meals = FirestoreChangeMapperLike { s, a, c -> MealPlanChangeMapper().write(s.batch, s.db, "h1", a, c) }

    private val eggs = GroceryItem(
        id = "i1", name = "Eggs", nameKey = "eggs", quantity = 12.0, unit = QuantityUnit.Pieces, note = null,
        category = Category.Dairy, checked = false, checkedBy = null, source = ItemSource.Manual, productId = null,
        recipeId = null, createdBy = "bob", createdAt = null,
    )
    private val itemPath = "households/h1/groceryLists/g1/items/i1"

    @Test
    fun `a grocery item comes back checked, by whom, with its creator and unknown fields`() {
        grocery.run(bob, GroceryChange.CreateItem("g1", eggs))
        server.docs.getValue(itemPath)["futureField"] = 1
        grocery.run(alice, GroceryChange.DeleteItem("g1", "i1"))
        // Bob's phone hadn't seen the deletion yet and checked it off.
        grocery.run(bob, GroceryChange.SetChecked("g1", "i1", true))
        grocery.run(alice, GroceryChange.RestoreItem("g1", eggs))

        val (_, kind, fields) = server.writes.last()
        assertEquals("merge", kind)
        assertEquals(restoreFields, fields.keys)
        val doc = server.docs.getValue(itemPath)
        assertFalse("deletedAt" in doc)
        assertEquals(1, doc["futureField"])
        assertEquals(FakeServer.SERVER_TIME, doc["checkedAt"])
        assertEquals(eggs.copy(checked = true, checkedBy = "bob"), server.snapshot(itemPath).toItem())
    }

    @Test
    fun `a grocery edit writes only what changed`() {
        grocery.run(bob, GroceryChange.CreateItem("g1", eggs))
        val before = GroceryItemFields.of(eggs)
        grocery.run(alice, GroceryChange.UpdateItem("g1", "i1", before, before.copy(note = "free range")))
        assertEquals(setOf("note", "updatedAt", "updatedBy"), server.writes.last().third.keys)
    }

    @Test
    fun `a recipe and a meal come back by removing the tombstone only`() {
        val soup = Recipe("r1", RecipeContent(title = "Soup"), createdBy = "bob")
        recipes.run(bob, RecipeChange.Create(soup))
        recipes.run(alice, RecipeChange.Delete("r1"))
        recipes.run(alice, RecipeChange.Restore(soup))
        assertEquals(restoreFields, server.writes.last().third.keys)
        val recipe = server.docs.getValue("households/h1/recipes/r1")
        assertFalse("deletedAt" in recipe)
        assertEquals("bob", recipe["createdBy"])

        val tacos = PlannedMeal(
            id = "p1", date = LocalDate.of(2026, 11, 10), slot = MealSlot.Dinner, position = 0, recipeId = null,
            title = "Tacos", servings = 2, cooks = listOf("bob"), note = null, createdBy = "bob",
        )
        meals.run(bob, MealPlanChange.Create(tacos))
        meals.run(alice, MealPlanChange.Delete("p1"))
        meals.run(alice, MealPlanChange.Restore(tacos))
        assertEquals(restoreFields, server.writes.last().third.keys)
        val meal = server.docs.values.single { it["title"] == "Tacos" }
        assertFalse("deletedAt" in meal)
        assertEquals("bob", meal["createdBy"])
        assertEquals(2L.toInt(), (meal["servings"] as Number).toInt())
    }
}
