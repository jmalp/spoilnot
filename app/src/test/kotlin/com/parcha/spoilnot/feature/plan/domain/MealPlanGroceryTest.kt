package com.parcha.spoilnot.feature.plan.domain

import com.parcha.spoilnot.feature.grocery.domain.GroceryItem
import com.parcha.spoilnot.feature.grocery.domain.ItemSource
import com.parcha.spoilnot.feature.pantry.domain.PantryData
import com.parcha.spoilnot.feature.pantry.domain.PantryFixtures.batch
import com.parcha.spoilnot.feature.pantry.domain.PantryFixtures.product
import com.parcha.spoilnot.feature.recipes.domain.RecipeGrocery
import com.parcha.spoilnot.feature.recipes.domain.recipe
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MealPlanGroceryTest {
    private val monday = LocalDate.of(2026, 10, 26)
    private val wednesday = monday.plusDays(2)
    private val empty = PantryData(emptyList(), emptyList(), false)

    private fun meal(
        id: String,
        date: LocalDate,
        recipeId: String?,
        servings: Int? = null,
        slot: MealSlot = MealSlot.Dinner,
        cooked: Boolean = false,
    ) = MealWeekTest().meal(id, date, slot).copy(
        recipeId = recipeId,
        servings = servings,
        cookedAt = if (cooked) Instant.parse("2026-10-28T19:00:00Z") else null,
    )

    @Test
    fun `only recipes still to cook this week are shopped for, in plan order`() {
        val recipes = listOf(recipe("tacos", "Tacos"), recipe("soup", "Soup"), recipe("pie", "Pie"))
        val meals = listOf(
            meal("past", monday, "tacos"), // before today
            meal("done", wednesday, "soup", cooked = true),
            meal("other", wednesday, null), // Leftovers: no recipe
            meal("gone", wednesday, "deleted"), // recipe deleted since
            meal("dinner", wednesday, "tacos"),
            meal("lunch", wednesday, "pie", slot = MealSlot.Lunch),
            meal("friday", monday.plusDays(4), "soup"),
        )
        val days = MealWeek.build(monday, meals, today = wednesday)
        assertEquals(
            listOf("lunch", "dinner", "friday"),
            MealPlanGrocery.toShop(days, recipes).map { it.first.id },
        )
    }

    @Test
    fun `a meal's servings scale counts up to whole items and show in the note`() {
        val soup = recipe("soup", "Soup", ingredients = listOf("3 eggs", "1 onion, diced"), servings = 4)
        val planned = meal("m1", wednesday, "soup", servings = 6)
        assertEquals(1.5, MealPlanGrocery.factor(planned, soup), 0.0)
        val lines = MealPlanGrocery.sections(listOf(planned to soup), empty, emptyList()).single().candidates
        // 4.5 eggs are 5 eggs to buy; 1.5 onions are 2.
        assertEquals(listOf(5.0, 2.0), lines.map { it.quantity })
        assertEquals("Soup: 4½ eggs", lines[0].note)
        assertTrue(lines.all { it.recipeId == "soup" })
        // Without servings on either side, the recipe is used as written.
        assertEquals(1.0, MealPlanGrocery.factor(planned.copy(servings = null), soup), 0.0)
    }

    @Test
    fun `lines already on the list or in the pantry start unchecked, and recipes without lines are left out`() {
        val tacos = recipe("tacos", "Tacos", ingredients = listOf("2 tortillas", "1 cup milk"))
        val bare = recipe("bare", "Bare")
        val pantry = PantryData(listOf(product("milk")), listOf(batch("b1", "milk")), false)
        val onList = GroceryItem(
            "i1", "Tortillas", "tortillas", null, null, null, null, false, null, ItemSource.Manual, null, null,
            null, null,
        )
        val sections = MealPlanGrocery.sections(
            listOf(meal("m1", wednesday, "tacos") to tacos, meal("m2", wednesday, "bare") to bare),
            pantry,
            listOf(onList),
        )
        val lines = sections.single().candidates
        assertTrue(lines[0].onList)
        assertFalse(lines[1].inPantry == null)
        assertTrue(lines.none { it.selectedByDefault })
    }

    @Test
    fun `the same item from two meals becomes one, with both notes`() {
        val tacos = recipe("tacos", "Tacos", ingredients = listOf("2 tortillas"))
        val soup = recipe("soup", "Soup", ingredients = listOf("3 tortillas"))
        val sections = MealPlanGrocery.sections(
            listOf(meal("m1", wednesday, "tacos") to tacos, meal("m2", wednesday, "soup") to soup),
            empty,
            emptyList(),
        )
        val merged = RecipeGrocery.merge(sections.flatMap { it.candidates }).single()
        assertEquals(5.0, merged.quantity!!, 0.0)
        assertEquals("Tacos: 2 tortillas; Soup: 3 tortillas", merged.note)
    }
}
