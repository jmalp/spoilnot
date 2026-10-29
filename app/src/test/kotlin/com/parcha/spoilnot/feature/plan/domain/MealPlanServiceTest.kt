package com.parcha.spoilnot.feature.plan.domain

import com.parcha.spoilnot.core.error.AppError
import com.parcha.spoilnot.core.write.Actor
import com.parcha.spoilnot.core.write.HouseholdChange
import com.parcha.spoilnot.core.write.HouseholdWriter
import com.parcha.spoilnot.feature.recipes.domain.recipe
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class MealPlanServiceTest {
    private val writer = RecordingWriter()
    private val service = MealPlanService(writer)
    private val actor = Actor("u1", "Sam")
    private val day = LocalDate.of(2026, 10, 29)
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
