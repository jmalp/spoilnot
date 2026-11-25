package com.parcha.spoilnot.feature.grocery.domain

import com.parcha.spoilnot.feature.pantry.domain.Category
import com.parcha.spoilnot.feature.pantry.domain.NameKey
import com.parcha.spoilnot.feature.pantry.domain.Product
import com.parcha.spoilnot.feature.recipes.domain.PantryMatch

/**
 * The aisle (category) for a grocery item typed without a matching product (DESIGN 5.3, after v1), so "bananas" goes
 * under Produce instead of Other and the store order can place it. The household's own products come first: the
 * product with the most words that are all in the name gives its aisle, so the household's own filing wins. Then a
 * built-in English word list: how it is kept ("frozen", "canned") first, then phrases of more than one word ("peanut
 * butter" is a pantry staple, not dairy), then what kind of product it is ("broth", "sauce", "paste", "dressing",
 * "juice"), then single food words, longest first. So "frozen spinach" is frozen and "chicken broth" a pantry staple
 * (after v1, LOGIC-17). Unknown names stay without an aisle (Other).
 */
object AisleGuess {
    private val WORD = Regex("""[^\p{L}\p{N}]+""")

    /** Phrases (singular, lowercase) to aisles. Multi-word phrases win over the words in them. */
    private val PHRASES: Map<String, Category> = buildMap {
        fun put(category: Category, vararg phrases: String) = phrases.forEach { put(it, category) }
        put(
            Category.Produce,
            "apple", "banana", "orange", "lemon", "lime", "grape", "berry", "strawberry", "blueberry", "raspberry",
            "avocado", "tomato", "potato", "sweet potato", "onion", "scallion", "shallot", "garlic", "carrot", "celery",
            "lettuce", "spinach", "kale", "arugula", "cucumber", "bell pepper", "zucchini", "broccoli", "cauliflower",
            "mushroom", "cilantro", "parsley", "basil", "mint", "ginger", "cabbage", "pear", "peach", "plum", "mango",
            "pineapple", "melon", "watermelon", "corn", "squash", "asparagus", "green bean", "pea", "radish", "beet",
            "leek", "jalapeno", "salad", "herb", "fruit", "vegetable",
        )
        put(
            Category.Dairy,
            "milk", "cheese", "yogurt", "yoghurt", "butter", "cream", "sour cream", "egg", "cottage cheese",
            "mozzarella", "cheddar", "parmesan", "feta", "kefir", "half and half", "creamer",
        )
        put(
            Category.MeatFish,
            "chicken", "beef", "pork", "turkey", "bacon", "sausage", "ham", "steak", "lamb", "fish", "salmon",
            "tuna steak", "cod", "shrimp", "prawn", "ground beef", "mince", "meat", "chorizo", "salami",
        )
        put(
            Category.Bakery,
            "bread", "bagel", "bun", "roll", "tortilla", "croissant", "muffin", "baguette", "pita", "cake", "loaf",
            "brioche", "naan",
        )
        put(Category.Frozen, "frozen", "ice cream", "ice", "frozen pea", "frozen pizza", "popsicle")
        put(
            Category.PantryStaples,
            "rice", "pasta", "spaghetti", "noodle", "flour", "sugar", "salt", "black pepper", "oil", "olive oil",
            "vinegar", "sauce", "soy sauce", "ketchup", "mustard", "mayonnaise", "spice", "cereal", "oat", "bean",
            "lentil", "chickpea", "broth", "stock", "honey", "jam", "peanut butter", "coffee", "tea", "canned",
            "tuna", "tomato sauce", "coconut milk", "baking soda", "baking powder", "yeast", "syrup",
        )
        put(Category.Drinks, "water", "sparkling water", "juice", "orange juice", "soda", "beer", "wine", "kombucha")
        put(
            Category.Snacks,
            "chip", "tortilla chip", "potato chip", "crisp", "cookie", "cracker", "chocolate", "candy", "nut",
            "popcorn", "pretzel",
        )
    }

    /** How it is kept: wins over every phrase that doesn't contain it ("frozen ground beef"). */
    private val KEPT: List<Pair<String, Category>> = listOf(
        "frozen" to Category.Frozen,
        "canned" to Category.PantryStaples,
        "tinned" to Category.PantryStaples,
    )

    /** What kind of product the food word makes: wins over single food words ("tomato paste", "apple juice"). */
    private val KINDS: List<Pair<String, Category>> = listOf(
        "broth" to Category.PantryStaples,
        "stock" to Category.PantryStaples,
        "sauce" to Category.PantryStaples,
        "paste" to Category.PantryStaples,
        "dressing" to Category.PantryStaples,
        "juice" to Category.Drinks,
    )

    private val LONGEST_FIRST = PHRASES.keys.sortedByDescending { it.split(' ').size * 100 + it.length }
    private val PHRASES_FIRST = LONGEST_FIRST.filter { ' ' in it }
    private val WORDS_LONGEST_FIRST = LONGEST_FIRST.filter { ' ' !in it }

    fun of(name: String, products: List<Product> = emptyList()): Category? {
        val words = words(name)
        if (words.isEmpty()) return null
        products
            .filter { p -> words(p.name).let { it.isNotEmpty() && words.containsAll(it) } }
            .maxByOrNull { words(it.name).size }
            ?.let { return it.category }
        val text = " ${words.joinToString(" ")} "
        fun has(phrase: String) = text.contains(" $phrase ")
        PHRASES_FIRST.firstOrNull { has(it) && KEPT.none { (word, _) -> word !in it.split(' ') && has(word) } }
            ?.let { return PHRASES.getValue(it) }
        KEPT.firstOrNull { has(it.first) }?.let { return it.second }
        KINDS.firstOrNull { has(it.first) }?.let { return it.second }
        return WORDS_LONGEST_FIRST.firstOrNull { has(it) }?.let { PHRASES.getValue(it) }
    }

    /**
     * The category Add item gives a new product (after v1, UX-09): the same guess as a typed grocery item, so Milk is
     * Dairy and eggs on both; Other when nothing matches.
     */
    fun forNewProduct(name: String, products: List<Product>): Category = of(name, products) ?: Category.Other

    /** In order, singular the same way as recipe matching, so phrases can be found. */
    private fun words(text: String): List<String> =
        NameKey.of(text).split(WORD).filter { it.isNotEmpty() }.map(PantryMatch::singular)
}
