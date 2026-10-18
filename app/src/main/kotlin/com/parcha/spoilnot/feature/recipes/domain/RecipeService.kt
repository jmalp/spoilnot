package com.parcha.spoilnot.feature.recipes.domain

import com.parcha.spoilnot.core.write.Actor
import com.parcha.spoilnot.core.write.HouseholdWriter
import com.parcha.spoilnot.core.write.Undo
import com.parcha.spoilnot.feature.grocery.domain.GroceryChange
import com.parcha.spoilnot.feature.grocery.domain.GroceryEventType
import com.parcha.spoilnot.feature.grocery.domain.GroceryItem
import com.parcha.spoilnot.feature.grocery.domain.GroceryRules
import com.parcha.spoilnot.feature.grocery.domain.ItemSource
import com.parcha.spoilnot.feature.pantry.domain.NameKey
import com.parcha.spoilnot.feature.pantry.domain.PantryData
import com.parcha.spoilnot.feature.pantry.domain.Product
import com.parcha.spoilnot.feature.pantry.domain.QuantityUnit
import javax.inject.Inject

sealed interface SaveResult {
    data class Saved(val recipeId: String) : SaveResult

    /** DESIGN 5.5: the link is already in the box; offer Open or Save a copy. */
    data class AlreadySaved(val existing: Recipe) : SaveResult
}

/** One ingredient in the Add to grocery list sheet (DESIGN 5.5). */
data class GroceryCandidate(
    val ingredient: Ingredient,
    val name: String,
    val quantity: Double?,
    val unit: QuantityUnit?,
    /** The measure that has no grocery unit, such as "2 tbsp", kept as the item's note. */
    val note: String?,
    /** "Probably in pantry": pre-unchecked, never decided silently (DESIGN 11.5). */
    val inPantry: Product?,
    /** Already on the list unchecked; also pre-unchecked so it is not added twice. */
    val onList: Boolean,
) {
    val selectedByDefault: Boolean get() = inPantry == null && !onList
}

object RecipeGrocery {
    private val GROCERY_UNITS = mapOf(
        "g" to QuantityUnit.Grams,
        "kg" to QuantityUnit.Kilograms,
        "ml" to QuantityUnit.Milliliters,
        "l" to QuantityUnit.Liters,
        "oz" to QuantityUnit.Ounces,
        "lb" to QuantityUnit.Pounds,
    )

    /** Units counted as pieces: "2 cans tomatoes" is 2 of an item called tomatoes. */
    private val COUNTED = setOf("can", "package", null)

    fun candidates(recipe: Recipe, pantry: PantryData, listItems: List<GroceryItem>): List<GroceryCandidate> {
        val onList = listItems.filter { !it.checked }.map { it.nameKey }.toSet()
        return recipe.content.ingredients.map { ingredient ->
            val parsed = if (ingredient.qty == null &&
                ingredient.name == null
            ) {
                IngredientParser.parse(ingredient.raw)
            } else {
                ingredient
            }
            val name = NameKey.clean(parsed.name ?: parsed.raw).take(GroceryRules.MAX_NAME_LENGTH).trim()
            val unit = GROCERY_UNITS[parsed.unit]
            val counted = parsed.qty != null && parsed.unit in COUNTED
            GroceryCandidate(
                ingredient = ingredient,
                name = name,
                quantity = parsed.qty?.takeIf { unit != null || counted },
                unit = unit,
                note = if (parsed.qty != null && unit == null && !counted) {
                    "${Scaling.formatQuantity(parsed.qty)} ${parsed.unit}"
                } else {
                    null
                },
                inPantry = PantryMatch.productFor(parsed.raw, pantry),
                onList = NameKey.of(name) in onList,
            )
        }
    }
}

/** Recipe box rules (DESIGN 5.5). Plans go to the HouseholdWriter. */
class RecipeService @Inject constructor(private val writer: HouseholdWriter) {
    /** Saves a new recipe, unless its link is already in the box and [saveCopy] is false. */
    suspend fun save(
        householdId: String,
        actor: Actor,
        content: RecipeContent,
        existing: List<Recipe>,
        saveCopy: Boolean = false,
    ): SaveResult {
        val clean = validated(content)
        if (!saveCopy) RecipeBox.duplicateOf(clean.sourceUrl, existing)?.let { return SaveResult.AlreadySaved(it) }
        val id = writer.newId()
        writer.apply(
            householdId,
            actor,
            listOf(
                RecipeChange.Create(Recipe(id, clean, createdBy = actor.uid)),
                RecipeChange.LogSaved(writer.newId(), id, clean.title),
            ),
        )
        return SaveResult.Saved(id)
    }

    suspend fun update(householdId: String, actor: Actor, recipeId: String, content: RecipeContent) =
        writer.apply(householdId, actor, listOf(RecipeChange.Update(recipeId, validated(content))))

    suspend fun setFavorite(householdId: String, actor: Actor, recipe: Recipe, favorite: Boolean) =
        writer.apply(householdId, actor, listOf(RecipeChange.SetFavorite(recipe.id, favorite)))

    suspend fun delete(householdId: String, actor: Actor, recipe: Recipe): Undo {
        writer.apply(householdId, actor, listOf(RecipeChange.Delete(recipe.id)))
        return Undo(listOf(RecipeChange.Restore(recipe)))
    }

    /** Adds the chosen ingredients to a grocery list with the source Recipe (DESIGN 5.5). Returns how many. */
    suspend fun addToGrocery(
        householdId: String,
        actor: Actor,
        listId: String,
        recipe: Recipe,
        chosen: List<GroceryCandidate>,
        pantry: PantryData,
        listName: String? = null,
    ): Int {
        val items = chosen.filter { it.name.isNotEmpty() }.map { c ->
            val key = NameKey.of(c.name)
            val product = pantry.products.firstOrNull { it.nameKey == key }
            GroceryItem(
                id = writer.newId(),
                name = product?.name ?: c.name,
                nameKey = key,
                quantity = c.quantity,
                unit = c.unit,
                note = c.note,
                category = product?.category ?: c.inPantry?.category,
                checked = false,
                checkedBy = null,
                source = ItemSource.Recipe,
                productId = product?.id,
                recipeId = recipe.id,
                createdBy = actor.uid,
                createdAt = null,
            )
        }
        if (items.isNotEmpty()) {
            val event = GroceryChange.LogEvent(
                writer.newId(),
                GroceryEventType.Added,
                recipe.title,
                listName,
                items.size,
            )
            writer.apply(householdId, actor, items.map { GroceryChange.CreateItem(listId, it) } + event)
        }
        return items.size
    }

    suspend fun undo(householdId: String, actor: Actor, undo: Undo) = writer.apply(householdId, actor, undo.changes)

    /** DESIGN 11.6 limits; the title is required. */
    fun validated(content: RecipeContent): RecipeContent {
        val title = RecipeText.clean(content.title, RecipeLimits.TITLE)
        require(title != null) { "A recipe needs a title" }
        return content.copy(
            title = title,
            sourceUrl = SourceUrl.canonical(content.sourceUrl),
            sourceName = RecipeText.clean(content.sourceName, RecipeLimits.SOURCE_NAME),
            imageUrl = content.imageUrl?.trim()?.takeIf { SourceUrl.isWebUrl(it) },
            description = content.description?.trim()?.take(RecipeLimits.DESCRIPTION)?.ifEmpty { null },
            servings = content.servings?.takeIf { it in 1..1000 },
            servingsText = RecipeText.clean(content.servingsText, RecipeLimits.SERVINGS_TEXT),
            prepMinutes = content.prepMinutes?.takeIf { it > 0 },
            cookMinutes = content.cookMinutes?.takeIf { it > 0 },
            totalMinutes = content.totalMinutes?.takeIf { it > 0 },
            ingredients = content.ingredients
                .filter { it.raw.isNotBlank() }
                .take(RecipeLimits.MAX_INGREDIENTS)
                .map { it.copy(raw = it.raw.trim().take(RecipeLimits.INGREDIENT)) },
            steps = content.steps
                .filter { it.text.isNotBlank() }
                .take(RecipeLimits.MAX_STEPS)
                .map { it.copy(text = it.text.trim().take(RecipeLimits.STEP)) },
            notes = content.notes?.trim()?.take(RecipeLimits.NOTES)?.ifEmpty { null },
            tags = RecipeText.tags(content.tags),
        )
    }
}

/** Fetches and extracts recipes from the web (DESIGN 11.1). Implemented in data/ with OkHttp. */
interface RecipeImportService {
    suspend fun fromUrl(url: String): ImportResult

    fun fromHtml(url: String, html: String): Extraction = RecipeExtractor.fromHtml(url, html)
}

sealed interface ImportResult {
    data class Found(val content: RecipeContent, val method: ExtractionMethod) : ImportResult

    /** Fetched, but no recipe or a blocked page (403, challenge, empty): try the live page (DESIGN 11.2). */
    data class TryBrowser(val url: String, val prefill: RecipeContent) : ImportResult

    /** Not an http or https link. */
    data object InvalidUrl : ImportResult

    data object Offline : ImportResult
}
