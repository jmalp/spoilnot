package com.parcha.spoilnot.feature.recipes.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Reads schema.org JSON-LD (DESIGN 11.3), tolerating the broken JSON real pages serve. */
object JsonLd {
    private val json = Json { isLenient = true }
    private val COMMENT = Regex("""<!--|-->""")
    private val CDATA = Regex("""^\s*(//\s*)?<!\[CDATA\[|(//\s*)?]]>\s*$""")

    /** Parses one script body, or returns null when it cannot be repaired. */
    fun parse(script: String): JsonElement? {
        val text = script.trimStart().removePrefix("\uFEFF").replace(COMMENT, "").replace(CDATA, "").trim()
        if (text.isEmpty()) return null
        return runCatching { json.parseToJsonElement(repair(text)) }.getOrNull()
    }

    /**
     * Removes trailing commas and escapes raw line breaks and tabs inside strings, without touching anything else
     * in the strings.
     */
    internal fun repair(text: String): String {
        val out = StringBuilder(text.length)
        var inString = false
        var escaped = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (inString) {
                when {
                    escaped -> {
                        out.append(c)
                        escaped = false
                    }

                    c == '\\' -> {
                        out.append(c)
                        escaped = true
                    }

                    c == '"' -> {
                        out.append(c)
                        inString = false
                    }

                    c == '\n' -> out.append("\\n")

                    c == '\r' -> out.append("\\r")

                    c == '\t' -> out.append("\\t")

                    c < ' ' -> Unit

                    else -> out.append(c)
                }
            } else if (c == '"') {
                inString = true
                out.append(c)
            } else if (c == ',') {
                var j = i + 1
                while (j < text.length && text[j].isWhitespace()) j++
                if (j < text.length && (text[j] == '}' || text[j] == ']')) Unit else out.append(c)
            } else {
                out.append(c)
            }
            i++
        }
        return out.toString()
    }

    /** Every node anywhere in [root] (top-level object, array, `@graph`, or nested) whose type is Recipe. */
    fun recipeNodes(root: JsonElement): List<JsonObject> {
        val found = mutableListOf<JsonObject>()
        fun walk(e: JsonElement, depth: Int) {
            if (depth > 12) return
            when (e) {
                is JsonObject -> {
                    if (isRecipe(e)) found += e
                    e.values.forEach { walk(it, depth + 1) }
                }

                is JsonArray -> e.forEach { walk(it, depth + 1) }

                else -> Unit
            }
        }
        walk(root, 0)
        return found
    }

    fun isRecipe(node: JsonObject): Boolean = types(node).any {
        it.substringAfterLast('/').substringAfterLast(':') ==
            "Recipe"
    }

    fun types(node: JsonObject): List<String> = strings(node["@type"])

    /** All strings in a value: a string, a number, or a list of them. */
    fun strings(e: JsonElement?): List<String> = when (e) {
        null, JsonNull -> emptyList()
        is JsonPrimitive -> listOf(e.content)
        is JsonArray -> e.flatMap { strings(it) }
        is JsonObject -> emptyList()
    }

    fun text(e: JsonElement?): String? = when (e) {
        is JsonPrimitive -> if (e == JsonNull) null else e.content
        is JsonArray -> e.firstNotNullOfOrNull { text(it) }
        is JsonObject -> text(e["name"]) ?: text(e["text"])
        null -> null
    }

    /** Maps a Recipe node to recipe content (the DESIGN 11.3 table). [pageUrl] resolves relative images. */
    fun map(node: JsonObject, pageUrl: String?): RecipeContent {
        val (servings, servingsText) = Yield.of(strings(node["recipeYield"]).ifEmpty { strings(node["yield"]) })
        val prep = Durations.minutes(text(node["prepTime"]))
        val cook = Durations.minutes(text(node["cookTime"]))
        val total = Durations.minutes(text(node["totalTime"]))
            ?: if (prep != null || cook != null) (prep ?: 0) + (cook ?: 0) else null
        val ingredients = strings(node["recipeIngredient"]).ifEmpty { strings(node["ingredients"]) }
            .mapNotNull { RecipeText.clean(it, RecipeLimits.INGREDIENT) }
            .take(RecipeLimits.MAX_INGREDIENTS)
            .map { IngredientParser.parse(it) }
        val tags = RecipeText.tags(
            strings(node["recipeCategory"]) + strings(node["recipeCuisine"]) + strings(node["keywords"]),
        )
        return RecipeContent(
            title = RecipeText.clean(text(node["name"]) ?: text(node["headline"]), RecipeLimits.TITLE).orEmpty(),
            sourceUrl = SourceUrl.canonical(pageUrl),
            sourceName = RecipeText.clean(author(node["author"]), RecipeLimits.SOURCE_NAME),
            imageUrl = image(node["image"], pageUrl),
            description = RecipeText.clean(text(node["description"]), RecipeLimits.DESCRIPTION),
            servings = servings,
            servingsText = servingsText,
            prepMinutes = prep,
            cookMinutes = cook,
            totalMinutes = total,
            ingredients = ingredients,
            steps = steps(node["recipeInstructions"]).take(RecipeLimits.MAX_STEPS),
            tags = tags,
            importMethod = ImportMethod.Web,
        )
    }

    private fun author(e: JsonElement?): String? = when (e) {
        is JsonArray -> e.firstNotNullOfOrNull { author(it) }
        is JsonObject -> text(e["name"])
        is JsonPrimitive -> if (e == JsonNull) null else e.content
        null -> null
    }

    /** A string, an ImageObject with `url`, or a list of them (the first). */
    fun image(e: JsonElement?, pageUrl: String?): String? = when (e) {
        is JsonPrimitive -> if (e == JsonNull) null else SourceUrl.resolve(pageUrl, e.content)
        is JsonArray -> e.firstNotNullOfOrNull { image(it, pageUrl) }
        is JsonObject -> image(e["url"] ?: e["contentUrl"] ?: e["@id"], pageUrl)
        null -> null
    }

    /**
     * A string (split on line breaks), a list of strings, HowToStep `text`, or HowToSection `itemListElement`
     * whose name becomes the step group.
     */
    fun steps(e: JsonElement?, group: String? = null): List<Step> = when (e) {
        null, JsonNull -> emptyList()

        is JsonPrimitive -> RecipeText.lines(e.content, RecipeLimits.STEP).map {
            Step(RecipeText.stripStepNumber(it), group)
        }

        is JsonArray -> e.flatMap { steps(it, group) }

        is JsonObject -> {
            val items = e["itemListElement"]
            val isSection = types(e).any { it.endsWith("HowToSection") || it.endsWith("ItemList") }
            when {
                items != null && (isSection || e["text"] == null) -> {
                    val name = RecipeText.clean(text(e["name"]), RecipeLimits.TITLE)
                    steps(items, name ?: group)
                }

                else -> {
                    val body = (e["text"] ?: e["description"] ?: e["name"])?.let { strings(it) }.orEmpty()
                    body.flatMap {
                        RecipeText.lines(it, RecipeLimits.STEP)
                    }.map { Step(RecipeText.stripStepNumber(it), group) }
                }
            }
        }
    }

    /** The recipe with the most ingredients among all nodes in [scripts] (DESIGN 11.3). */
    fun best(scripts: List<String>): JsonObject? = scripts
        .mapNotNull { parse(it) }
        .flatMap { recipeNodes(it) }
        .maxByOrNull { (strings(it["recipeIngredient"]).ifEmpty { strings(it["ingredients"]) }).size }
}
