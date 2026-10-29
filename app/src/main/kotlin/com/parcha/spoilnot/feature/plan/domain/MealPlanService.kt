package com.parcha.spoilnot.feature.plan.domain

import com.parcha.spoilnot.core.write.Actor
import com.parcha.spoilnot.core.write.HouseholdWriter
import com.parcha.spoilnot.core.write.Undo
import com.parcha.spoilnot.feature.pantry.domain.NameKey
import com.parcha.spoilnot.feature.recipes.domain.Recipe
import java.time.LocalDate
import javax.inject.Inject

/** Meal plan actions (DESIGN 5.9). Every member may change the plan; removing has Undo. */
class MealPlanService @Inject constructor(private val writer: HouseholdWriter) {
    /** Plans [recipe] in a slot, after the meals already there; servings start at the recipe's. Returns the id. */
    suspend fun addRecipe(
        householdId: String,
        actor: Actor,
        date: LocalDate,
        slot: MealSlot,
        recipe: Recipe,
        cooks: List<String>,
        inSlot: List<PlannedMeal>,
    ): String = add(householdId, actor, date, slot, recipe.id, recipe.title, recipe.content.servings, cooks, inSlot)

    /** Plans a meal without a recipe, such as "Leftovers" or "Eat out". Returns the id. */
    suspend fun addOther(
        householdId: String,
        actor: Actor,
        date: LocalDate,
        slot: MealSlot,
        title: String,
        cooks: List<String>,
        inSlot: List<PlannedMeal>,
    ): String = add(householdId, actor, date, slot, null, title, null, cooks, inSlot)

    suspend fun update(
        householdId: String,
        actor: Actor,
        meal: PlannedMeal,
        servings: Int?,
        cooks: List<String>,
        note: String?,
    ) = writer.apply(
        householdId,
        actor,
        listOf(MealPlanChange.Update(meal.id, validServings(servings), validCooks(cooks), validNote(note))),
    )

    suspend fun remove(householdId: String, actor: Actor, meal: PlannedMeal): Undo {
        writer.apply(householdId, actor, listOf(MealPlanChange.Delete(meal.id)))
        return Undo(listOf(MealPlanChange.Restore(meal)))
    }

    suspend fun undo(householdId: String, actor: Actor, undo: Undo) = writer.apply(householdId, actor, undo.changes)

    private suspend fun add(
        householdId: String,
        actor: Actor,
        date: LocalDate,
        slot: MealSlot,
        recipeId: String?,
        title: String,
        servings: Int?,
        cooks: List<String>,
        inSlot: List<PlannedMeal>,
    ): String {
        val meal = PlannedMeal(
            id = writer.newId(),
            date = date,
            slot = slot,
            position = MealWeek.nextPosition(inSlot),
            recipeId = recipeId,
            title = validTitle(title),
            servings = validServings(servings),
            cooks = validCooks(cooks),
            note = null,
        )
        writer.apply(householdId, actor, listOf(MealPlanChange.Create(meal)))
        return meal.id
    }

    internal companion object {
        fun validTitle(title: String): String {
            val clean = NameKey.clean(title)
            require(clean.isNotEmpty()) { "A meal needs a name" }
            return clean.take(MealPlanLimits.TITLE).trim()
        }

        fun validServings(servings: Int?): Int? =
            servings?.coerceIn(MealPlanLimits.MIN_SERVINGS, MealPlanLimits.MAX_SERVINGS)

        fun validCooks(cooks: List<String>): List<String> =
            cooks.filter { it.isNotBlank() }.distinct().take(MealPlanLimits.MAX_COOKS)

        fun validNote(note: String?): String? = note?.trim()?.take(MealPlanLimits.NOTE)?.trim()?.ifEmpty { null }
    }
}
