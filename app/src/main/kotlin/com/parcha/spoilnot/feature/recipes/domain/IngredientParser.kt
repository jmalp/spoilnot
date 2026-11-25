package com.parcha.spoilnot.feature.recipes.domain

import com.parcha.spoilnot.feature.pantry.domain.NameKey
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToLong

/**
 * Best-effort ingredient parsing (DESIGN 11.5): a leading quantity (integers, decimals with a point or a comma,
 * thousands separators, unicode fractions, mixed numbers, ranges using the lower value), then a known unit, then the
 * name (text after the first comma and anything in parentheses dropped). Lines without a leading quantity keep only
 * `raw`. Any whitespace counts as a space ([NameKey.WHITESPACE]).
 */
object IngredientParser {
    /** Where the quantity is in the line, so scaling can replace just that part and keep the rest. */
    data class Quantity(val value: Double, val max: Double?, val end: Int)

    private val VULGAR = mapOf(
        '½' to 1.0 / 2, '⅓' to 1.0 / 3, '⅔' to 2.0 / 3, '¼' to 1.0 / 4, '¾' to 3.0 / 4,
        '⅕' to 1.0 / 5, '⅖' to 2.0 / 5, '⅗' to 3.0 / 5, '⅘' to 4.0 / 5, '⅙' to 1.0 / 6, '⅚' to 5.0 / 6,
        '⅛' to 1.0 / 8, '⅜' to 3.0 / 8, '⅝' to 5.0 / 8, '⅞' to 7.0 / 8,
    )
    private val V = VULGAR.keys.joinToString("")

    /**
     * "1,000" is a thousand (a comma before exactly three digits); "1,5" and "1,25" are decimals (after v1, LOGIC-15).
     */
    private const val NUMBER = """\d+\s+\d+/\d+|\d+/\d+|\d{1,3}(?:,\d{3})+(?!\d)|""" +
        """\d+(?:\.\d+|,\d{1,2}(?!\d))?(?:\s*[VULGAR])?|\.\d+|[VULGAR]"""
    private val THOUSANDS = Regex("""^\d{1,3}(?:,\d{3})+$""")
    private val ONE = NUMBER.replace("VULGAR", V)
    private val QUANTITY = Regex("""^\s*($ONE)(?:\s*(?:-|–|—|to|or)\s*($ONE))?""", RegexOption.IGNORE_CASE)

    /** Canonical unit to the words that mean it (DESIGN 11.5 list, with common spellings). */
    val UNITS: Map<String, List<String>> = linkedMapOf(
        "cup" to listOf("cups", "cup", "c"),
        "tbsp" to listOf("tablespoons", "tablespoon", "tbsps", "tbsp", "tbs", "tbl"),
        "tsp" to listOf("teaspoons", "teaspoon", "tsps", "tsp"),
        "kg" to listOf("kilograms", "kilogram", "kilos", "kilo", "kgs", "kg"),
        "g" to listOf("grams", "gram", "grammes", "gramme", "gr", "g"),
        "ml" to listOf("milliliters", "milliliter", "millilitres", "millilitre", "mls", "ml"),
        "l" to listOf("liters", "liter", "litres", "litre", "l"),
        "oz" to listOf("ounces", "ounce", "oz"),
        "lb" to listOf("pounds", "pound", "lbs", "lb"),
        "pinch" to listOf("pinches", "pinch"),
        "clove" to listOf("cloves", "clove"),
        "can" to listOf("cans", "can", "tins", "tin"),
        "package" to listOf("packages", "package", "packets", "packet", "pkgs", "pkg"),
    )
    private val UNIT_WORD: Map<String, String> = UNITS.flatMap { (unit, words) -> words.map { it to unit } }.toMap()
    private val UNIT = Regex(
        """^\s*(${UNIT_WORD.keys.sortedByDescending { it.length }.joinToString("|")})\.?(?![\p{L}])""",
        RegexOption.IGNORE_CASE,
    )
    private val PARENS = Regex("""\([^)]*\)""")
    private val LEADING_PARENS = Regex("""^\s*\([^)]*\)""")
    private val OF = Regex("""^of\s+""", RegexOption.IGNORE_CASE)
    private val SPACES = Regex("""\s+""")

    fun parse(raw: String, group: String? = null): Ingredient {
        val line = NameKey.plainSpaces(raw).replace('⁄', '/')
        val quantity = quantity(line) ?: return Ingredient(raw = raw, group = group)
        var rest = line.substring(quantity.end)
        // "1 (14 ounce) can tomatoes": the size in parentheses sits between the quantity and the unit.
        rest = rest.replace(LEADING_PARENS, "")
        val unitMatch = UNIT.find(rest)
        val unit = unitMatch?.let { UNIT_WORD[it.groupValues[1].lowercase()] }
        if (unitMatch != null) rest = rest.substring(unitMatch.range.last + 1)
        val name = rest.replace(PARENS, " ")
            .substringBefore(',')
            .replace(SPACES, " ")
            .trim()
            .replace(OF, "")
            .trim()
            .ifEmpty { null }
        return Ingredient(raw = raw, qty = quantity.value, unit = unit, name = name, group = group)
    }

    /** [rest] (what follows a quantity) without a leading size in parentheses and unit word: " tsp salt" is "salt". */
    fun withoutUnit(rest: String): String {
        val text = NameKey.plainSpaces(rest).replace(LEADING_PARENS, "")
        val unit = UNIT.find(text) ?: return text.trim()
        return text.substring(unit.range.last + 1).trim().replace(OF, "")
    }

    /** The leading quantity of [line], or null when it does not start with one. */
    fun quantity(line: String): Quantity? {
        val text = NameKey.plainSpaces(line).replace('⁄', '/')
        val match = QUANTITY.find(text) ?: return null
        val end = match.range.last + 1
        val value = number(match.groupValues[1]) ?: return null
        val max = match.groupValues[2].takeIf { it.isNotEmpty() }?.let(::number)
        if (value <= 0.0) return null
        return Quantity(value, max?.takeIf { it > value }, end)
    }

    private fun number(text: String): Double? {
        val t = text.trim()
        val vulgar = t.lastOrNull()?.let { VULGAR[it] }
        if (vulgar != null) {
            val whole = t.dropLast(1).trim()
            return if (whole.isEmpty()) vulgar else whole.toDoubleOrNull()?.plus(vulgar)
        }
        val parts = t.split(SPACES)
        if (parts.size == 2) return parts[0].toDoubleOrNull()?.let { w -> fraction(parts[1])?.let { w + it } }
        return when {
            '/' in t -> fraction(t)
            THOUSANDS.matches(t) -> t.replace(",", "").toDoubleOrNull()
            else -> t.replace(',', '.').toDoubleOrNull()
        }
    }

    private fun fraction(text: String): Double? {
        val (n, d) = text.split('/').takeIf { it.size == 2 } ?: return null
        val denominator = d.toDoubleOrNull()?.takeIf { it != 0.0 } ?: return null
        return n.toDoubleOrNull()?.div(denominator)
    }
}

/** Servings scaling (DESIGN 5.5): only quantities that could be parsed change; the rest of the line is kept. */
object Scaling {
    /** Below this a scaled amount is "a pinch" instead of a number that would read 0 (after v1, LOGIC-13). */
    const val PINCH_BELOW = 1.0 / 16

    /** The servings steppers' usual top. */
    const val STEPPER_MAX = 100

    /**
     * Where a servings stepper stops (after v1, LOGIC-13): [STEPPER_MAX], or the recipe's own servings when it makes
     * more ("Makes 120 meatballs"), or the [current] value when that is higher; never above
     * [RecipeLimits.MAX_SERVINGS].
     */
    fun maxServings(recipeServings: Int?, current: Int? = null): Int =
        maxOf(STEPPER_MAX, recipeServings ?: 0, current ?: 0).coerceAtMost(RecipeLimits.MAX_SERVINGS)

    fun factor(original: Int?, target: Int?): Double =
        if (original == null || target == null || original <= 0) 1.0 else target.toDouble() / original

    /**
     * [raw] at [factor] times its amount. An amount under [PINCH_BELOW] becomes [pinch] (strings.xml
     * `recipe_scaled_pinch`, "a pinch of") followed by the name, without the unit: "1/8 tsp salt" at a tenth is "a
     * pinch of salt".
     */
    fun line(raw: String, factor: Double, pinch: String): String {
        if (abs(factor - 1.0) < 1e-9) return raw
        val quantity = IngredientParser.quantity(raw) ?: return raw
        val rest = raw.replace('⁄', '/').substring(quantity.end)
        if (quantity.value * factor < PINCH_BELOW) return pinchOf(rest, pinch)
        val scaled = formatQuantity(quantity.value * factor) +
            (quantity.max?.let { "–" + formatQuantity(it * factor) } ?: "")
        return scaled + rest
    }

    private fun pinchOf(rest: String, pinch: String): String {
        val name = IngredientParser.withoutUnit(rest).trim()
        return if (name.isEmpty()) pinch.trim() else "${pinch.trim()} $name"
    }

    private val FRACTIONS = listOf(
        0.0 to "", 1.0 / 8 to "⅛", 1.0 / 4 to "¼", 1.0 / 3 to "⅓", 3.0 / 8 to "⅜", 1.0 / 2 to "½",
        5.0 / 8 to "⅝", 2.0 / 3 to "⅔", 3.0 / 4 to "¾", 7.0 / 8 to "⅞", 1.0 to "",
    )

    /** 1.5 becomes "1½", 0.333 becomes "⅓", 12.4 becomes "12"; other values keep up to two decimals. */
    fun formatQuantity(value: Double): String {
        if (value >= 10) return value.roundToLong().toString()
        val whole = floor(value)
        val rest = value - whole
        val (nearest, symbol) = FRACTIONS.minBy { abs(it.first - rest) }
        if (abs(nearest - rest) < 0.02) {
            val w = (whole + if (nearest == 1.0) 1 else 0).toLong()
            return when {
                symbol.isEmpty() -> w.toString()
                w == 0L -> symbol
                else -> "$w$symbol"
            }
        }
        return "%.2f".format(java.util.Locale.ROOT, value).trimEnd('0').trimEnd('.')
    }
}
