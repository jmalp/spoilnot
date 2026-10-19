package com.parcha.spoilnot.feature.recipes.domain

import com.parcha.spoilnot.feature.pantry.domain.NameKey
import com.parcha.spoilnot.feature.pantry.domain.Product

/**
 * What an ingredient is called on a grocery list (DESIGN 5.5, after v1): "1 extra-large onion, finely diced" is
 * bought as "Onion". Size, preparation and freshness words around the name are dropped, as is anything after a
 * comma, in parentheses, after "or" and phrases such as "to taste". Words that change what is bought (ground,
 * whole, frozen, baby, hot) stay.
 */
object ShoppingName {
    /** Size words, dropped only at the start ("large eggs"). */
    private val SIZE = setOf(
        "extra-large", "extra large", "x-large", "xl", "large", "medium", "small", "big", "jumbo", "heaping",
        "heaped", "level", "generous",
    )

    /** Preparation and freshness words, dropped at the start or the end ("onion finely chopped"). */
    private val PREP = setOf(
        "fresh", "freshly", "ripe", "chopped", "diced", "minced", "sliced", "grated", "shredded", "peeled", "crushed",
        "finely", "roughly", "coarsely", "thinly", "thickly", "cubed", "halved", "quartered", "mashed", "melted",
        "softened", "beaten", "toasted", "cooked", "uncooked", "drained", "rinsed", "trimmed", "pitted", "seeded",
        "deseeded", "deveined", "boneless", "skinless", "packed", "lightly", "firmly", "sifted", "room-temperature",
        "room temperature", "about", "approximately", "and",
    )

    /** Phrases that end the name: "salt to taste", "parsley for garnish", "butter or margarine". */
    private val TAIL = Regex(
        """\s+(?:to taste|for (?:garnish|serving|the \w+)|as needed|if needed|optional|divided|plus more.*|or .*)$""",
        RegexOption.IGNORE_CASE,
    )
    private val PARENS = Regex("""\([^)]*\)""")
    private val SPACES = Regex("""\s+""")
    private val EDGE_PUNCTUATION = Regex("""^[\s,.;:*-]+|[\s,.;:*-]+$""")

    /** The grocery name for [name] (the parsed ingredient name, or the whole line when it has no quantity). */
    fun of(name: String): String {
        var text = name.replace(PARENS, " ").substringBefore(',').replace(SPACES, " ").trim()
        text = text.replace(TAIL, "").replace(EDGE_PUNCTUATION, "")
        var words = text.split(' ').filter { it.isNotEmpty() }
        words = dropLeading(words, SIZE + PREP)
        words = dropTrailing(words, PREP)
        val result = words.joinToString(" ").replace(EDGE_PUNCTUATION, "")
        val cleaned = result.ifEmpty { NameKey.clean(name) }
        return cleaned.replaceFirstChar { it.uppercaseChar() }
    }

    private fun dropLeading(words: List<String>, drop: Set<String>): List<String> {
        var i = 0
        while (i < words.size - 1) {
            val two = if (i + 1 < words.size) "${words[i]} ${words[i + 1]}".lowercase() else null
            i += when {
                two != null && two in drop && i + 2 < words.size -> 2
                words[i].lowercase() in drop -> 1
                else -> return words.drop(i)
            }
        }
        return words.drop(i)
    }

    private fun dropTrailing(words: List<String>, drop: Set<String>): List<String> {
        var end = words.size
        while (end > 1 && words[end - 1].lowercase() in drop) end--
        return words.take(end)
    }
}

/**
 * Finds the household product a recipe ingredient means (DESIGN 5.5, after v1), in stock or not, so the grocery
 * item uses its name, unit and category, and Add to pantry adds to it instead of creating a near-duplicate. Names
 * the household taught a product ([Product.aliases], from Change) come first. Otherwise words are compared singular,
 * and a product matches when its words are all in the ingredient with at most one more ("Black pepper" for "ground
 * black pepper"); the most shared words win. A product with more words than the ingredient never matches: "Green
 * onions" is not "onion".
 */
object ProductLink {
    fun find(name: String, products: List<Product>): Product? {
        val key = NameKey.of(name)
        products.firstOrNull { it.nameKey == key }?.let { return it }
        val wanted = PantryMatch.words(name)
        if (wanted.isEmpty()) return null
        // A name someone taught a product, by renaming a recipe item, wins over a word match.
        products.firstOrNull { p -> p.aliases.any { it == key || PantryMatch.words(it) == wanted } }?.let { return it }
        val words = products.associateWith { PantryMatch.words(it.nameKey) }.filterValues { it.isNotEmpty() }
        val contained = words.filter { (_, w) -> wanted.containsAll(w) && wanted.size - w.size <= 1 }
        return if (contained.isEmpty()) null else contained.best()
    }

    private fun Map<Product, Set<String>>.best(): Product = entries.sortedWith(
        compareByDescending<Map.Entry<Product, Set<String>>> {
            it.value.size
        }.thenBy { it.key.nameKey },
    )
        .first().key
}
