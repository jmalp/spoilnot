package com.parcha.spoilnot.feature.plan.domain

import com.parcha.spoilnot.core.error.AppError
import com.parcha.spoilnot.core.write.Actor
import com.parcha.spoilnot.core.write.HouseholdChange
import com.parcha.spoilnot.core.write.HouseholdWriter
import com.parcha.spoilnot.feature.pantry.domain.PantryChange
import com.parcha.spoilnot.feature.pantry.domain.PantryData
import com.parcha.spoilnot.feature.pantry.domain.PantryFixtures.batch
import com.parcha.spoilnot.feature.pantry.domain.PantryFixtures.product
import com.parcha.spoilnot.feature.pantry.domain.PantryService
import com.parcha.spoilnot.feature.recipes.domain.RecipeChange
import com.parcha.spoilnot.feature.recipes.domain.RecipeService
import com.parcha.spoilnot.feature.recipes.domain.recipe
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MealPlanServiceTest {
    private val writer = RecordingWriter()
    private val actor = Actor("u1", "Sam")
    private val day = LocalDate.of(2026, 10, 29)
    private val clock = Clock.fixed(day.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
    private val service = MealPlanService(writer, PantryService(writer, clock), RecipeService(writer))
    private val existing = MealWeekTest().meal("m0", date = day, position = 2)

    @Test
    fun `a recipe is planned after the slot's meals, with the recipe's servings and title`() = runTest {
        val id = service.addRecipe(
            "h",
            actor,
            day,
            MealSlot.Dinner,
            recipe("r1", "Lasagna", servings = 6),
            listOf("u1", "u2", "u1"),
            listOf(existing),
        )
        val created = (writer.last.single() as MealPlanChange.Create).meal
        assertEquals("id0", id)
        assertEquals(
            PlannedMeal(
                id = "id0", date = day, slot = MealSlot.Dinner, position = 3, recipeId = "r1", title = "Lasagna",
                servings = 6, cooks = listOf("u1", "u2"), note = null,
            ),
            created,
        )
    }

    @Test
    fun `something else has a cleaned name and no recipe, and needs a name`() = runTest {
        service.addOther("h", actor, day, MealSlot.Lunch, "  Eat   out ", emptyList(), emptyList())
        val created = (writer.last.single() as MealPlanChange.Create).meal
        assertEquals("Eat out" to null, created.title to created.recipeId)
        assertEquals(0L, created.position)
        assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.runBlocking {
                service.addOther("h", actor, day, MealSlot.Lunch, "   ", emptyList(), emptyList())
            }
        }
    }

    @Test
    fun `changes are kept within the limits`() = runTest {
        val cooks = (1..25).map { "u$it" }
        service.update("h", actor, existing, servings = 500, cooks = cooks, note = "  ")
        val update = writer.last.single() as MealPlanChange.Update
        assertEquals(MealPlanLimits.MAX_SERVINGS, update.servings)
        assertEquals(MealPlanLimits.MAX_COOKS, update.cooks.size)
        assertNull(update.note)
        service.update("h", actor, existing, servings = 0, cooks = emptyList(), note = " Double batch ")
        val second = writer.last.single() as MealPlanChange.Update
        assertEquals(1 to "Double batch", second.servings to second.note)
    }

    @Test
    fun `removing leaves a tombstone and Undo puts the meal back as it was`() = runTest {
        val undo = service.remove("h", actor, existing)
        assertEquals(listOf(MealPlanChange.Delete("m0")), writer.last)
        assertEquals(listOf(MealPlanChange.Restore(existing)), undo.changes)
        service.undo("h", actor, undo)
        assertEquals(undo.changes, writer.last)
    }

    @Test
    fun `cooking a recipe meal takes it off the pantry, marks the meal and logs it, all in one Undo`() = runTest {
        val tacos = recipe("r1", "Tacos", ingredients = listOf("2 eggs"))
        val meal = existing.copy(recipeId = "r1", title = "Tacos")
        val pantry = PantryData(listOf(product("eggs")), listOf(batch("b1", "eggs", quantity = 6.0)), false)
        val undo = service.cooked("h", actor, meal, tacos, mapOf("eggs" to 2.0), pantry)
        val written = writer.last
        assertEquals(PantryChange.AddToBatch("b1", -2.0), written.first())
        assertEquals(MealPlanChange.SetCooked("m0", true), written.filterIsInstance<MealPlanChange>().single())
        val event = written.filterIsInstance<RecipeChange.LogCooked>().single()
        assertEquals("Tacos", event.title)
        assertEquals(
            listOf(
                PantryChange.AddToBatch("b1", 2.0),
                MealPlanChange.SetCooked("m0", false),
                PantryChange.DeleteEvent(event.eventId),
            ),
            undo.changes,
        )
    }

    @Test
    fun `a meal without a recipe, or nothing used, only marks the meal`() = runTest {
        val empty = PantryData(emptyList(), emptyList(), false)
        val undo = service.cooked("h", actor, existing, null, emptyMap(), empty)
        assertEquals(listOf(MealPlanChange.SetCooked("m0", true)), writer.last)
        assertEquals(listOf(MealPlanChange.SetCooked("m0", false)), undo.changes)
        // A recipe meal with nothing taken off still logs that it was cooked.
        val withRecipe = service.cooked("h", actor, existing, recipe("r1", "Soup"), emptyMap(), empty)
        assertEquals(2, writer.last.size)
        assertTrue(writer.last.any { it is RecipeChange.LogCooked })
        assertTrue(withRecipe.changes.any { it is PantryChange.DeleteEvent })
        service.notCooked("h", actor, existing)
        assertEquals(listOf(MealPlanChange.SetCooked("m0", false)), writer.last)
    }

    @Test
    fun `moving goes after the meals in the new slot and Undo puts it back`() = runTest {
        val friday = day.plusDays(1)
        val there = MealWeekTest().meal("m1", date = friday, slot = MealSlot.Lunch, position = 3)
        val undo = service.move("h", actor, existing, friday, MealSlot.Lunch, listOf(there))!!
        assertEquals(listOf(MealPlanChange.Move("m0", friday, MealSlot.Lunch, 4)), writer.last)
        assertEquals(listOf(MealPlanChange.Move("m0", day, MealSlot.Dinner, 2)), undo.changes)
        // The same place is no move.
        assertNull(service.move("h", actor, existing, day, MealSlot.Dinner, emptyList()))
    }

    @Test
    fun `copying last week creates the copies, and Undo removes them`() = runTest {
        val lastStart = LocalDate.of(2026, 10, 19)
        val previous = listOf(MealWeekTest().meal("p1", date = day.minusWeeks(1)).copy(title = "Tacos"))
        val (count, undo) = service.copyWeek("h", actor, previous, lastStart, lastStart.plusWeeks(1), emptyList())!!
        val created = writer.last.map { (it as MealPlanChange.Create).meal }
        assertEquals(1, count)
        assertEquals(listOf("id0" to day), created.map { it.id to it.date })
        assertEquals(listOf(MealPlanChange.Delete("id0")), undo.changes)
        assertNull(service.copyWeek("h", actor, emptyList(), lastStart, lastStart.plusWeeks(1), emptyList()))
    }

    @Test
    fun `clearing a week removes every meal with one Undo`() = runTest {
        val other = MealWeekTest().meal("m1", date = day.plusDays(1))
        val undo = service.clear("h", actor, listOf(existing, other))!!
        assertEquals(listOf(MealPlanChange.Delete("m0"), MealPlanChange.Delete("m1")), writer.last)
        assertEquals(listOf(MealPlanChange.Restore(existing), MealPlanChange.Restore(other)), undo.changes)
        assertNull(service.clear("h", actor, emptyList()))
    }

    private class RecordingWriter : HouseholdWriter {
        var last: List<HouseholdChange> = emptyList()
        private var next = 0

        override fun newId() = "id${next++}"

        override suspend fun apply(householdId: String, actor: Actor, changes: List<HouseholdChange>) {
            last = changes
        }

        override val errors: Flow<AppError> = emptyFlow()
    }
}
