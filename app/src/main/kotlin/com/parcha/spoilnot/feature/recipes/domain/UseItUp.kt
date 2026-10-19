package com.parcha.spoilnot.feature.recipes.domain

import com.parcha.spoilnot.feature.pantry.domain.ExpiryStatus
import com.parcha.spoilnot.feature.pantry.domain.PantryData
import com.parcha.spoilnot.feature.pantry.domain.Product
import com.parcha.spoilnot.feature.pantry.domain.StockBatch
import java.time.LocalDate

/** A recipe ingredient and the household product it means, if any (DESIGN 5.5 linking). */
data class LinkedIngredient(val ingredient: Ingredient, val product: Product?)

/** Recipe lines matched to the household's products, the same way the grocery sheet does. */
object RecipeIngredients {
    fun linked(recipe: Recipe, products: List<Product>): List<LinkedIngredient> =
        linked(recipe, ProductLink.Index(products))

    /**
     * With [only] set, lines sharing no word with it are not looked up: every link needs a shared word (the same
     * name, an alias, or all of the product's words), so they could not link to anything in it anyway.
     */
    fun linked(recipe: Recipe, index: ProductLink.Index, only: Set<String>? = null): List<LinkedIngredient> =
        recipe.content.ingredients.map { ingredient ->
            val name = shoppingName(ingredient)
            val wanted = PantryMatch.words(name)
            val product = if (only != null && wanted.none { it in only }) {
                null
            } else {
                ProductLink.find(name, index, wanted)
            }
            LinkedIngredient(ingredient, product)
        }

    private fun shoppingName(ingredient: Ingredient): String {
        val parsed = if (ingredient.qty == null && ingredient.name == null) {
            IngredientParser.parse(ingredient.raw)
        } else {
            ingredient
        }
        return ShoppingName.of(parsed.name ?: parsed.raw)
    }
}

/** One expiring product a suggested recipe uses, with the batch that expires first. */
data class ExpiringUse(val product: Product, val batch: StockBatch)

/** A saved recipe that uses food expiring soon (DESIGN 5.6, after v1). */
data class UseItUpSuggestion(val recipe: Recipe, val uses: List<ExpiringUse>)

/**
 * "Use it up" on Home (DESIGN 5.6, after v1): saved recipes that use what expires today or in the next few days,
 * the ones using the most expiring products first, then the most urgent. Expired food is left out: it belongs in
 * Needs attention, not in a recipe.
 */
object UseItUp {
    /** At most this many recipes on Home. */
    const val MAX = 3

    fun suggestions(recipes: List<Recipe>, pantry: PantryData, today: LocalDate): List<UseItUpSuggestion> {
        val expiring = pantry.batches
            .filter { it.quantity > 0 && it.expiryDate != null }
            .filter { ExpiryStatus.of(it.expiryDate, today).let { s -> s.needsAttention && s != ExpiryStatus.Expired } }
            .groupBy { it.productId }
            .mapValues { (_, batches) -> batches.minBy { it.expiryDate!! } }
        if (expiring.isEmpty()) return emptyList()
        val index = ProductLink.Index(pantry.products)
        // Only lines that share a word with an expiring product are linked (after v1, for large recipe boxes).
        val expiringWords = pantry.products.filter { it.id in expiring }.flatMap { index.linkWords(it) }.toSet()
        return recipes
            .mapNotNull { recipe ->
                val uses = RecipeIngredients.linked(recipe, index, expiringWords)
                    .mapNotNull { it.product }
                    .distinctBy { it.id }
                    .mapNotNull { product -> expiring[product.id]?.let { ExpiringUse(product, it) } }
                    .sortedBy { it.batch.expiryDate }
                if (uses.isEmpty()) null else UseItUpSuggestion(recipe, uses)
            }
            .sortedWith(
                compareByDescending<UseItUpSuggestion> { it.uses.size }
                    .thenBy { it.uses.first().batch.expiryDate }
                    .thenByDescending { it.recipe.favorite }
                    .thenBy { it.recipe.title.lowercase() },
            )
            .take(MAX)
    }
}
