package com.parcha.spoilnot.feature.plan.domain

import com.parcha.spoilnot.core.write.Actor
import com.parcha.spoilnot.core.write.HouseholdChange
import com.parcha.spoilnot.core.write.HouseholdWriter
import com.parcha.spoilnot.core.write.Undo
import com.parcha.spoilnot.feature.pantry.domain.NameKey
import com.parcha.spoilnot.feature.pantry.domain.PantryData
import com.parcha.spoilnot.feature.pantry.domain.PantryService
import com.parcha.spoilnot.feature.recipes.domain.Recipe
import com.parcha.spoilnot.feature.recipes.domain.RecipeService
import java.time.LocalDate
import javax.inject.Inject

/** Meal plan actions (DESIGN 5.9). Every member may change the plan; removing and cooking have Undo. */
class MealPlanService @Inject constructor(
    private val writer: HouseholdWriter,
    private val pantry: PantryService,
    private val recipes: RecipeService,
) {
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

    /**
     * "Cooked" (DESIGN 5.9): marks [meal] cooked and, for a recipe, takes [amounts] (product id to amount) off the
     * pantry and logs "cooked" in Recent activity, the same as I cooked this on the recipe (DESIGN 5.5). One Undo puts
     * all of it back.
     */
    suspend fun cooked(
        householdId: String,
        actor: Actor,
        meal: PlannedMeal,
        recipe: Recipe?,
        amounts: Map<String, Double>,
        pantryData: PantryData,
    ): Undo {
        val extra = mutableListOf<HouseholdChange>(MealPlanChange.SetCooked(meal.id, true))
        val undoExtra = mutableListOf<HouseholdChange>(MealPlanChange.SetCooked(meal.id, false))
        if (recipe != null) {
            val (event, removeEvent) = recipes.cookedEvent(recipe)
            extra += event
            undoExtra += removeEvent
        }
        pantry.use(householdId, actor, amounts, pantryData, extra, undoExtra)?.let { return it }
        // Nothing taken off the pantry: only the meal (and the event) change.
        writer.apply(householdId, actor, extra)
        return Undo(undoExtra)
    }

    /** Moves [meal] to [date] and [slot], after the meals there ([inSlot]). Undo puts it back where it was. */
    suspend fun move(
        householdId: String,
        actor: Actor,
        meal: PlannedMeal,
        date: LocalDate,
        slot: MealSlot,
        inSlot: List<PlannedMeal>,
    ): Undo? {
        if (meal.date == date && meal.slot == slot) return null
        val position = MealWeek.nextPosition(inSlot.filter { it.id != meal.id })
        writer.apply(householdId, actor, listOf(MealPlanChange.Move(meal.id, date, slot, position)))
        return Undo(listOf(MealPlanChange.Move(meal.id, meal.date, meal.slot, meal.position)))
    }

    /**
     * Copy last week (DESIGN 5.9): [previous] week's meals into the week starting [to], skipping ones already there.
     * Returns how many were copied and an Undo that removes them, or null when there was nothing to copy.
     */
    suspend fun copyWeek(
        householdId: String,
        actor: Actor,
        previous: List<PlannedMeal>,
        from: LocalDate,
        to: LocalDate,
        existing: List<PlannedMeal>,
    ): Pair<Int, Undo>? {
        val copies = MealWeek.copy(previous, from, to, existing).map { it.copy(id = writer.newId()) }
        if (copies.isEmpty()) return null
        writer.apply(householdId, actor, copies.map { MealPlanChange.Create(it) })
        return copies.size to Undo(copies.map { MealPlanChange.Delete(it.id) })
    }

    /** Clear week: removes [meals] (one week's), with one Undo that puts them all back. Null when it is empty. */
    suspend fun clear(householdId: String, actor: Actor, meals: List<PlannedMeal>): Undo? {
        if (meals.isEmpty()) return null
        writer.apply(householdId, actor, meals.map { MealPlanChange.Delete(it.id) })
        return Undo(meals.map { MealPlanChange.Restore(it) })
    }

    /** "Not cooked yet": for a meal marked cooked by mistake. The pantry is not touched. */
    suspend fun notCooked(householdId: String, actor: Actor, meal: PlannedMeal) =
        writer.apply(householdId, actor, listOf(MealPlanChange.SetCooked(meal.id, false)))

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
