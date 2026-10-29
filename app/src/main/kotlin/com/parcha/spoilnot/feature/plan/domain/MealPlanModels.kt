package com.parcha.spoilnot.feature.plan.domain

import com.parcha.spoilnot.core.write.HouseholdChange
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/** The meal slots of a day (DESIGN 5.9), in the order they are shown. */
enum class MealSlot(val wire: String) {
    Breakfast("breakfast"),
    Lunch("lunch"),
    Dinner("dinner"),
    ;

    companion object {
        fun fromWire(value: String?) = entries.firstOrNull { it.wire == value }
    }
}

/**
 * households/{hid}/meals/{id} (DESIGN 5.9, 8.2): one planned meal. [recipeId] is null for a meal without a recipe
 * ("Leftovers"); [title] is the recipe's title when it was planned, so a deleted recipe still has a name. [cooks]
 * are member uids. [position] orders meals within a slot.
 */
data class PlannedMeal(
    val id: String,
    val date: LocalDate,
    val slot: MealSlot,
    val position: Long,
    val recipeId: String?,
    val title: String,
    val servings: Int?,
    val cooks: List<String>,
    val note: String?,
    val cookedAt: Instant? = null,
    val cookedBy: String? = null,
    val createdAt: Instant? = null,
    val createdBy: String? = null,
    val pendingSync: Boolean = false,
)

/** One write in a meal plan, applied by the HouseholdWriter. */
sealed interface MealPlanChange : HouseholdChange {
    data class Create(val meal: PlannedMeal) : MealPlanChange

    /** Puts a removed meal back under its old id (Undo). */
    data class Restore(val meal: PlannedMeal) : MealPlanChange

    data class Update(val mealId: String, val servings: Int?, val cooks: List<String>, val note: String?) :
        MealPlanChange

    /** A tombstone, so other phones' changes-only listeners see it go (DESIGN 9.8). */
    data class Delete(val mealId: String) : MealPlanChange

    /** Marks a meal cooked by the actor now (server time), or not cooked again (DESIGN 5.9). */
    data class SetCooked(val mealId: String, val cooked: Boolean) : MealPlanChange
}

/** Meals in a date range, and whether the server has confirmed them yet. */
data class MealPlanData(val meals: List<PlannedMeal>, val fromCache: Boolean)

interface MealPlanRepository {
    /** Planned meals from [from] to [to] inclusive (changes-only, DESIGN 9.8). */
    fun observe(householdId: String, from: LocalDate, to: LocalDate): Flow<MealPlanData>
}

/** DESIGN 5.9 limits, also enforced by the security rules. */
object MealPlanLimits {
    const val TITLE = 120
    const val NOTE = 200
    const val MAX_COOKS = 20
    const val MIN_SERVINGS = 1
    const val MAX_SERVINGS = 100

    /** Past meals are kept this long, then the daily job deletes them. */
    const val KEEP_DAYS = 28L
}
