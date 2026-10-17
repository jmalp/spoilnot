package com.parcha.spoilnot.feature.recipes.domain

import com.parcha.spoilnot.feature.pantry.domain.NameKey
import com.parcha.spoilnot.feature.pantry.domain.PantryData
import com.parcha.spoilnot.feature.pantry.domain.Product
import java.time.Instant

sealed interface RecipeFilter {
    data object All : RecipeFilter

    data object Favorites : RecipeFilter

    data class Tag(val tag: String) : RecipeFilter
}

enum class RecipeSort { Recent, AToZ }

data class RecipeQuery(
    val search: String = "",
    val filter: RecipeFilter = RecipeFilter.All,
    val sort: RecipeSort = RecipeSort.Recent,
)

/** The recipe box list (DESIGN 5.5): search by title, ingredient and tag; All, Favorites and tag chips; sort. */
object RecipeBox {
    fun list(recipes: List<Recipe>, query: RecipeQuery): List<Recipe> {
        val search = NameKey.of(query.search)
        val filtered = recipes.filter { recipe ->
            val chip = when (val f = query.filter) {
                RecipeFilter.All -> true
                RecipeFilter.Favorites -> recipe.favorite
                is RecipeFilter.Tag -> recipe.content.tags.any { NameKey.of(it) == NameKey.of(f.tag) }
            }
            chip && (search.isEmpty() || matches(recipe, search))
        }
        return when (query.sort) {
            RecipeSort.Recent -> filtered.sortedWith(
                compareByDescending<Recipe, Instant?>(nullsLast()) { it.createdAt }.thenBy { NameKey.of(it.title) },
            )

            RecipeSort.AToZ -> filtered.sortedBy { NameKey.of(it.title) }
        }
    }

    /** User tags for the chips, the most used first. */
    fun tags(recipes: List<Recipe>): List<String> = recipes
        .flatMap { it.content.tags }
        .groupBy { NameKey.of(it) }
        .entries
        .sortedWith(compareByDescending<Map.Entry<String, List<String>>> { it.value.size }.thenBy { it.key })
        .map { it.value.first() }

    /** The recipe already saved from the same page, for "Already saved" (DESIGN 5.5). */
    fun duplicateOf(sourceUrl: String?, recipes: List<Recipe>): Recipe? {
        val key = SourceUrl.sameKey(sourceUrl) ?: return null
        return recipes.firstOrNull { SourceUrl.sameKey(it.content.sourceUrl) == key }
    }

    private fun matches(recipe: Recipe, search: String): Boolean {
        val c = recipe.content
        return NameKey.of(c.title).contains(search) ||
            c.tags.any { NameKey.of(it).contains(search) } ||
            c.ingredients.any { NameKey.of(it.raw).contains(search) }
    }
}

/**
 * "Probably in pantry" (DESIGN 11.5): a product matches an ingredient when every word of its nameKey,
 * singularized, appears in the ingredient line. Only a hint; the user decides.
 */
object PantryMatch {
    private val WORD = Regex("""[^\p{L}\p{N}]+""")

    fun singular(word: String): String = when {
        word.length <= 3 -> word

        word.endsWith("ies") -> word.dropLast(3) + "y"

        word.endsWith("oes") -> word.dropLast(2)

        word.endsWith("ches") || word.endsWith("shes") || word.endsWith("sses") || word.endsWith("xes") ->
            word.dropLast(2)

        word.endsWith("ss") || word.endsWith("us") || word.endsWith("is") -> word

        word.endsWith("s") -> word.dropLast(1)

        else -> word
    }

    fun words(text: String): Set<String> =
        NameKey.of(text).split(WORD).filter { it.isNotEmpty() }.map { singular(it) }.toSet()

    fun matches(nameKey: String, line: String): Boolean {
        val product = words(nameKey)
        return product.isNotEmpty() && words(line).containsAll(product)
    }

    /** The in-stock product that best matches [line] (the one with the most words), if any. */
    fun productFor(line: String, pantry: PantryData): Product? {
        val inStock = pantry.batches.filter { it.quantity > 0 }.map { it.productId }.toSet()
        val lineWords = words(line)
        return pantry.products
            .filter { it.id in inStock }
            .filter { p -> words(p.nameKey).let { it.isNotEmpty() && lineWords.containsAll(it) } }
            .maxByOrNull { words(it.nameKey).size }
    }
}

/**
 * The editor's one-per-line text (DESIGN 5.5 Create manually). A line ending in a colon, such as "For the sauce:",
 * starts a group for the lines after it.
 */
object RecipeLines {
    private const val MAX_GROUP = 80

    fun ingredientsToText(ingredients: List<Ingredient>): String = toText(ingredients.map { it.group to it.raw })

    fun stepsToText(steps: List<Step>): String = toText(steps.map { it.group to it.text })

    fun ingredients(text: String): List<Ingredient> = grouped(text, RecipeLimits.INGREDIENT)
        .take(RecipeLimits.MAX_INGREDIENTS)
        .map { (group, line) -> IngredientParser.parse(line, group) }

    fun steps(text: String): List<Step> = grouped(text, RecipeLimits.STEP)
        .take(RecipeLimits.MAX_STEPS)
        .map { (group, line) -> Step(line, group) }

    fun tags(text: String): List<String> = RecipeText.tags(listOf(text))

    private fun toText(lines: List<Pair<String?, String>>): String {
        val out = mutableListOf<String>()
        var group: String? = null
        for ((g, line) in lines) {
            if (g != group && g != null) out += "$g:"
            group = g
            out += line
        }
        return out.joinToString("\n")
    }

    private fun grouped(text: String, limit: Int): List<Pair<String?, String>> {
        var group: String? = null
        val out = mutableListOf<Pair<String?, String>>()
        for (raw in text.lines()) {
            val line = RecipeText.clean(raw, limit) ?: continue
            if (line.endsWith(':') && line.length <= MAX_GROUP) {
                group = line.dropLast(1).trim().ifEmpty { null }
            } else {
                out += group to line
            }
        }
        return out
    }
}
