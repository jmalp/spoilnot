package com.parcha.spoilnot.feature.recipes.domain

import com.parcha.spoilnot.feature.pantry.domain.PantryData
import com.parcha.spoilnot.feature.pantry.domain.Product
import com.parcha.spoilnot.feature.pantry.domain.QuantityUnit
import kotlin.math.abs
import kotlin.math.ceil

/**
 * One pantry product a recipe uses, in the "I cooked this" sheet (DESIGN 5.5, after v1).
 *
 * [suggested] is how much to take off, in the product's unit, when the recipe says it in a way the pantry can count:
 * a count for a product kept in pieces ("3 eggs"), or the same unit ("500 g rice" from rice kept in grams). A measure
 * the pantry cannot count ("2 tbsp soy sauce" from a bottle) has none: the line starts unchosen with the whole stock,
 * so choosing it means "I finished it", and the amount can be changed.
 */
data class CookLine(
    val product: Product,
    /** The recipe lines that mean this product ("2 eggs" and "1 egg, for brushing"). */
    val lines: List<String>,
    /** In stock now, in [Product.unit]. */
    val inStock: Double,
    val suggested: Double?,
) {
    val selectedByDefault: Boolean get() = suggested != null
    val defaultAmount: Double get() = suggested?.coerceAtMost(inStock) ?: inStock
}

object Cooking {
    private val SAME_UNIT = mapOf(
        "g" to QuantityUnit.Grams,
        "kg" to QuantityUnit.Kilograms,
        "ml" to QuantityUnit.Milliliters,
        "l" to QuantityUnit.Liters,
        "oz" to QuantityUnit.Ounces,
        "lb" to QuantityUnit.Pounds,
    )
    private val COUNTED = setOf("can", "package", null)

    /**
     * The in-stock products [recipe] uses, at [factor] times its servings (the detail screen's stepper), in recipe
     * order. Lines that mean the same product add up. Scaled, a product kept in pieces is suggested in whole pieces,
     * rounded up like the grocery list ("4½ eggs" takes 5, after v1, LOGIC-13); at the recipe's own servings the
     * amount is as written ("½ onion"). [words] give tiny scaled amounts their text ([Scaling.line]).
     */
    fun lines(recipe: Recipe, pantry: PantryData, words: RecipeWords, factor: Double = 1.0): List<CookLine> {
        val stock = pantry.batches.filter { it.quantity > 0 }.groupBy { it.productId }
            .mapValues { (_, batches) -> batches.sumOf { it.quantity } }
        val linked = RecipeIngredients.linked(recipe, pantry.products)
            .mapNotNull { l -> l.product?.takeIf { stock.containsKey(it.id) }?.let { it to l.ingredient } }
        return linked.groupBy({ it.first.id }) { it.second }.map { (id, ingredients) ->
            val product = linked.first { it.first.id == id }.first
            val amounts = ingredients.map { amount(it, product) }
            val total = if (amounts.all { it != null }) amounts.sumOf { it!! } * factor else null
            CookLine(
                product = product,
                lines = ingredients.map { Scaling.line(it.raw, factor, words.pinch) },
                inStock = stock.getValue(id),
                suggested = total?.let { if (product.unit == QuantityUnit.Pieces) wholePieces(it, factor) else it },
            )
        }
    }

    /** Scaled pieces rounded up, ignoring floating-point dust: 4.5 is 5, 3.0000000001 is 3. */
    private fun wholePieces(value: Double, factor: Double): Double =
        if (abs(factor - 1.0) < 1e-9) value else ceil(value - 1e-9)

    private fun amount(ingredient: Ingredient, product: Product): Double? {
        val parsed = if (ingredient.qty == null && ingredient.name == null) {
            IngredientParser.parse(ingredient.raw)
        } else {
            ingredient
        }
        val qty = parsed.qty ?: return null
        return when {
            parsed.unit in COUNTED && product.unit == QuantityUnit.Pieces -> qty
            SAME_UNIT[parsed.unit] == product.unit -> qty
            else -> null
        }
    }
}
