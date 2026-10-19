package com.parcha.spoilnot.feature.recipes.domain

import com.parcha.spoilnot.core.write.HouseholdChange
import java.time.Instant
import kotlinx.coroutines.flow.Flow

/** One ingredient line. [raw] is always kept; the parsed fields are best effort (DESIGN 11.5). */
data class Ingredient(
    val raw: String,
    val qty: Double? = null,
    val unit: String? = null,
    val name: String? = null,
    val group: String? = null,
)

data class Step(val text: String, val group: String? = null)

enum class ImportMethod(val wire: String) {
    Manual("manual"),
    Web("web"),
    ;

    companion object {
        fun fromWire(value: String?) = entries.firstOrNull { it.wire == value } ?: Manual
    }
}

/** What a person can edit or an import can fill in (DESIGN 8.2 recipes). */
data class RecipeContent(
    val title: String,
    val sourceUrl: String? = null,
    val sourceName: String? = null,
    val imageUrl: String? = null,
    val description: String? = null,
    val servings: Int? = null,
    val servingsText: String? = null,
    val prepMinutes: Int? = null,
    val cookMinutes: Int? = null,
    val totalMinutes: Int? = null,
    val ingredients: List<Ingredient> = emptyList(),
    val steps: List<Step> = emptyList(),
    val notes: String? = null,
    val tags: List<String> = emptyList(),
    val importMethod: ImportMethod = ImportMethod.Manual,
)

/** households/{hid}/recipes/{recipeId}. */
data class Recipe(
    val id: String,
    val content: RecipeContent,
    val favorite: Boolean = false,
    val rating: Int? = null,
    val createdAt: Instant? = null,
    val createdBy: String? = null,
) {
    val title: String get() = content.title
}

/** One write in a recipe plan, applied by the HouseholdWriter (together with grocery changes when needed). */
sealed interface RecipeChange : HouseholdChange {
    data class Create(val recipe: Recipe) : RecipeChange

    data class Update(val recipeId: String, val content: RecipeContent) : RecipeChange

    data class SetFavorite(val recipeId: String, val favorite: Boolean) : RecipeChange

    data class Delete(val recipeId: String) : RecipeChange

    /** Puts a deleted recipe back under its old id (Undo). */
    data class Restore(val recipe: Recipe) : RecipeChange

    /** The recipe_saved event in the activity log (DESIGN 8.2). */
    data class LogSaved(val eventId: String, val recipeId: String, val title: String) : RecipeChange

    /** "Sam cooked Green curry" in Recent activity (after v1). */
    data class LogCooked(val eventId: String, val recipeId: String, val title: String) : RecipeChange
}

/** Limits from DESIGN 11.6, also enforced by the security rules. */
object RecipeLimits {
    const val TITLE = 200
    const val INGREDIENT = 300
    const val STEP = 2_000
    const val MAX_INGREDIENTS = 100
    const val MAX_STEPS = 100
    const val MAX_TAGS = 8
    const val TAG = 40
    const val DESCRIPTION = 2_000
    const val NOTES = 5_000
    const val URL = 2_000
    const val SOURCE_NAME = 100
    const val SERVINGS_TEXT = 100
}

/** The recipe box. Writes go through the HouseholdWriter. */
interface RecipeRepository {
    /** The whole collection, from the first visit to the Recipes tab (DESIGN 9.1). */
    fun observe(householdId: String): Flow<RecipeBoxData>
}

data class RecipeBoxData(val recipes: List<Recipe>, val fromCache: Boolean)
