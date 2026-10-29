package com.parcha.spoilnot.feature.plan.domain

import com.parcha.spoilnot.feature.grocery.domain.GroceryItem
import com.parcha.spoilnot.feature.pantry.domain.PantryData
import com.parcha.spoilnot.feature.recipes.domain.GroceryCandidate
import com.parcha.spoilnot.feature.recipes.domain.Recipe
import com.parcha.spoilnot.feature.recipes.domain.RecipeGrocery
import com.parcha.spoilnot.feature.recipes.domain.Scaling

/** A planned recipe and its ingredients as grocery lines, scaled to the meal's servings. */
data class MealGroceries(val meal: PlannedMeal, val recipe: Recipe, val candidates: List<GroceryCandidate>)

/**
 * "Add to grocery list" for a week (DESIGN 5.9): the ingredients of the recipes still to cook, chosen the same way as
 * from one recipe (DESIGN 5.5). Lines that look like pantry items or are already on the list start unchecked; the
 * same item from two meals becomes one when added.
 */
object MealPlanGrocery {
    /** Recipe meals from today on that are not cooked yet and whose recipe is still saved, in plan order. */
    fun toShop(days: List<PlanDay>, recipes: List<Recipe>): List<Pair<PlannedMeal, Recipe>> {
        val byId = recipes.associateBy { it.id }
        return days.filter { !it.isPast }.flatMap { day ->
            MealSlot.entries.flatMap { slot -> day.slots[slot].orEmpty() }
        }.mapNotNull { meal ->
            val recipe = meal.recipeId?.let(byId::get)
            if (recipe == null || meal.cookedAt != null) null else meal to recipe
        }
    }

    /** How much of the recipe the meal needs: its servings over the recipe's, or 1 when either is unknown. */
    fun factor(meal: PlannedMeal, recipe: Recipe): Double = Scaling.factor(recipe.content.servings, meal.servings)

    fun sections(
        meals: List<Pair<PlannedMeal, Recipe>>,
        pantry: PantryData,
        listItems: List<GroceryItem>,
    ): List<MealGroceries> = meals.map { (meal, recipe) ->
        MealGroceries(meal, recipe, RecipeGrocery.candidates(recipe, pantry, listItems, factor(meal, recipe)))
    }.filter { it.candidates.isNotEmpty() }
}
