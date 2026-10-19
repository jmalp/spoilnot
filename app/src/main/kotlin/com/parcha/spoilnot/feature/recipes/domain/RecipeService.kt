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
    /** The shopping name: the linked product's name, or the cleaned ingredient name ("Onion"). */
    val name: String,
    /** A shopping amount: the product's usual amount, or a count from the recipe ("3 eggs"); never a measure. */
    val quantity: Double?,
    val unit: QuantityUnit?,
    /** Where it comes from, with the recipe's amount: "Pancakes: 1 extra large onion, diced". */
    val note: String?,
    /** The household product this ingredient means, in stock or not (after v1); null for a new one. */
    val product: Product?,
    /** "Probably in pantry": pre-unchecked, never decided silently (DESIGN 11.5). */
    val inPantry: Product?,
    /** Already on the list unchecked; also pre-unchecked so it is not added twice. */
    val onList: Boolean,
) {
    val selectedByDefault: Boolean get() = inPantry == null && !onList
}

/**
 * Recipe ingredients as grocery items (DESIGN 5.5, after v1): shopping names, linked to the household's products,
 * with shopping amounts. Recipe measures (a tablespoon, a cup, 200 g) are what the recipe uses, not what is bought,
 * so they stay in the note.
 */
object RecipeGrocery {
    /** Units counted as pieces: "2 cans tomatoes" is 2 of an item called tomatoes. */
    private val COUNTED = setOf("can", "package", null)

    /** Longest note kept on a grocery item. */
    const val MAX_NOTE_LENGTH = 200

    fun candidates(recipe: Recipe, pantry: PantryData, listItems: List<GroceryItem>): List<GroceryCandidate> {
        val unchecked = listItems.filter { !it.checked }
        val onListKeys = unchecked.map { it.nameKey }.toSet()
        val onListProducts = unchecked.mapNotNull { it.productId }.toSet()
        return recipe.content.ingredients.map { ingredient ->
            val parsed = if (ingredient.qty == null && ingredient.name == null) {
                IngredientParser.parse(ingredient.raw)
            } else {
                ingredient
            }
            val cleaned = ShoppingName.of(parsed.name ?: parsed.raw)
            val product = ProductLink.find(cleaned, pantry.products)
            val name = (product?.name ?: cleaned).take(GroceryRules.MAX_NAME_LENGTH).trim()
            val usual = product?.let { it.buyQuantity ?: it.lastAddedQuantity }
            val count = parsed.qty?.takeIf {
                parsed.unit in COUNTED &&
                    (product == null || product.unit == QuantityUnit.Pieces)
            }
            GroceryCandidate(
                ingredient = ingredient,
                name = name,
                quantity = usual ?: count,
                unit = if (usual != null) product.unit else null,
                note = note(recipe.content.title, ingredient.raw),
                product = product,
                inPantry = PantryMatch.productFor(parsed.raw, pantry),
                onList = NameKey.of(name) in onListKeys || (product != null && product.id in onListProducts),
            )
        }
    }

    private fun note(title: String, raw: String): String {
        val text = "${title.trim()}: ${raw.trim()}".trim()
        return if (text.length <= MAX_NOTE_LENGTH) text else text.take(MAX_NOTE_LENGTH - 1).trimEnd() + "…"
    }

    /**
     * The items to add for [chosen]: one per shopping name, so "1 onion, diced" and "1 onion, sliced" become one
     * Onion. Quantities add up when both have one in the same unit; notes are joined.
     */
    fun merge(chosen: List<GroceryCandidate>): List<GroceryCandidate> = chosen
        .filter { it.name.isNotEmpty() }
        .groupBy { NameKey.of(it.name) }
        .values
        .map { same ->
            same.reduce { a, b ->
                val quantity = when {
                    a.quantity == null -> b.quantity
                    b.quantity == null -> a.quantity
                    a.unit == b.unit && a.product == null -> a.quantity + b.quantity
                    else -> a.quantity // a product's usual amount covers both
                }
                val notes = listOfNotNull(a.note, b.note).joinToString("; ")
                a.copy(
                    quantity = quantity,
                    note = if (notes.length <= MAX_NOTE_LENGTH) notes else notes.take(MAX_NOTE_LENGTH - 1) + "…",
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
        val items = RecipeGrocery.merge(chosen).map { c ->
            val key = NameKey.of(c.name)
            val product = c.product ?: pantry.products.firstOrNull { it.nameKey == key }
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
