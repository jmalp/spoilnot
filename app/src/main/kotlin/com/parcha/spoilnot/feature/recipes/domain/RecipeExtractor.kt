package com.parcha.spoilnot.feature.recipes.domain

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/** How a recipe was found. Heuristic results are labeled "Check this recipe" in the preview (DESIGN 11.4). */
enum class ExtractionMethod { JsonLd, Microdata, Heuristic }

sealed interface Extraction {
    data class Found(val content: RecipeContent, val method: ExtractionMethod) : Extraction

    /** No recipe on the page; [prefill] has what the page offers (title, image, link) for Create manually. */
    data class NotFound(val prefill: RecipeContent) : Extraction
}

/**
 * What the built-in browser reads from the live page with evaluateJavascript (DESIGN 11.2). [html] is the
 * serialized DOM, sent only when no JSON-LD mentions a Recipe (deviation, see docs/DECISIONS.md).
 */
data class PageSnapshot(
    val url: String,
    val title: String?,
    val ld: List<String>,
    val ogImage: String?,
    val html: String? = null,
)

/** Finds the recipe in a page: JSON-LD, then microdata, then heuristics (DESIGN 11.2 to 11.4). */
object RecipeExtractor {
    fun fromHtml(url: String, html: String): Extraction {
        val doc = Jsoup.parse(html, url)
        val ld = doc.select("script[type=application/ld+json]").map { it.data() }
        JsonLd.best(ld)?.let { node -> found(JsonLd.map(node, url), doc, ExtractionMethod.JsonLd)?.let { return it } }
        Microdata.recipe(doc)?.let { node ->
            found(JsonLd.map(node, url), doc, ExtractionMethod.Microdata)?.let { return it }
        }
        Heuristics.recipe(doc, url)?.let { return Extraction.Found(it, ExtractionMethod.Heuristic) }
        return Extraction.NotFound(prefill(doc, url))
    }

    fun fromPage(page: PageSnapshot): Extraction {
        JsonLd.best(page.ld)?.let { node ->
            val content = complete(JsonLd.map(node, page.url), page.title, page.ogImage, page.url)
            if (isUsable(content)) return Extraction.Found(content, ExtractionMethod.JsonLd)
        }
        if (page.html != null) return fromHtml(page.url, page.html)
        return Extraction.NotFound(
            RecipeContent(
                title = RecipeText.clean(page.title, RecipeLimits.TITLE).orEmpty(),
                sourceUrl = SourceUrl.canonical(page.url),
                imageUrl = SourceUrl.resolve(page.url, page.ogImage),
            ),
        )
    }

    private fun found(content: RecipeContent, doc: Document, method: ExtractionMethod): Extraction.Found? {
        val complete = complete(content, ogTitle(doc), meta(doc, "og:image"), doc.location())
        return if (isUsable(complete)) Extraction.Found(complete, method) else null
    }

    /** Fills a missing title or image from the page's Open Graph tags. */
    private fun complete(content: RecipeContent, title: String?, image: String?, url: String) = content.copy(
        title = content.title.ifEmpty { RecipeText.clean(title, RecipeLimits.TITLE).orEmpty() },
        imageUrl = content.imageUrl ?: SourceUrl.resolve(url, image),
    )

    private fun isUsable(content: RecipeContent) =
        content.title.isNotEmpty() && (content.ingredients.isNotEmpty() || content.steps.isNotEmpty())

    internal fun prefill(doc: Document, url: String) = RecipeContent(
        title = RecipeText.clean(ogTitle(doc), RecipeLimits.TITLE).orEmpty(),
        sourceUrl = SourceUrl.canonical(url),
        imageUrl = SourceUrl.resolve(url, meta(doc, "og:image")),
    )

    internal fun ogTitle(doc: Document): String? = meta(doc, "og:title") ?: doc.title().ifBlank { null }

    internal fun meta(doc: Document, property: String): String? =
        doc.selectFirst("meta[property=$property], meta[name=$property]")?.attr("content")?.ifBlank { null }
}

/** schema.org microdata (`itemtype` containing schema.org/Recipe), turned into JSON-LD for the same mapper. */
object Microdata {
    fun recipe(doc: Document): JsonObject? = doc.select("[itemscope][itemtype]")
        .filter { el -> el.attr("itemtype").split(' ').any { it.trimEnd('/').endsWith("schema.org/Recipe", true) } }
        .map { item(it) }
        .maxByOrNull { JsonLd.strings(it["recipeIngredient"]).ifEmpty { JsonLd.strings(it["ingredients"]) }.size }

    /** One item scope: its type and the properties that belong to it, not to nested items. */
    fun item(scope: Element): JsonObject {
        val props = linkedMapOf<String, MutableList<JsonElement>>()
        fun walk(e: Element) {
            for (child in e.children()) {
                val names = child.attr("itemprop").split(' ').filter { it.isNotBlank() }
                val nested = child.hasAttr("itemscope")
                if (names.isNotEmpty()) {
                    val value = if (nested) item(child) else JsonPrimitive(value(child))
                    names.forEach { props.getOrPut(it) { mutableListOf() } += value }
                }
                if (!nested) walk(child)
            }
        }
        walk(scope)
        val type = scope.attr("itemtype").split(' ').first().substringAfterLast('/')
        val fields = props.mapValues { (_, values) -> values.singleOrNull() ?: JsonArray(values) }
        return JsonObject(mapOf("@type" to JsonPrimitive(type)) + fields)
    }

    private fun value(e: Element): String = when (e.normalName()) {
        "meta" -> e.attr("content")
        "img", "audio", "video", "source", "embed", "iframe" -> e.absUrl("src").ifEmpty { e.attr("src") }
        "a", "link", "area" -> e.absUrl("href").ifEmpty { e.attr("href") }
        "time" -> e.attr("datetime").ifEmpty { e.text() }
        "data", "meter" -> e.attr("value")
        else -> if (e.hasAttr("content")) e.attr("content") else RecipeText.blockText(e)
    }
}

/**
 * The last resort (DESIGN 11.4): recipe-plugin containers (class names starting `wprm-recipe-` or
 * `tasty-recipes-`), with `og:title` and `og:image`.
 */
object Heuristics {
    fun recipe(doc: Document, url: String): RecipeContent? {
        val plugin = wprm(doc) ?: tasty(doc) ?: return null
        val ingredients = plugin.ingredients.take(RecipeLimits.MAX_INGREDIENTS)
        val steps = plugin.steps.take(RecipeLimits.MAX_STEPS)
        if (ingredients.isEmpty() && steps.isEmpty()) return null
        val title = RecipeText.clean(plugin.title ?: RecipeExtractor.ogTitle(doc), RecipeLimits.TITLE) ?: return null
        val (servings, servingsText) = Yield.of(listOfNotNull(plugin.servings))
        return RecipeContent(
            title = title,
            sourceUrl = SourceUrl.canonical(url),
            imageUrl = SourceUrl.resolve(url, RecipeExtractor.meta(doc, "og:image")),
            servings = servings,
            servingsText = servingsText,
            ingredients = ingredients,
            steps = steps,
            importMethod = ImportMethod.Web,
        )
    }

    private class Plugin(
        val title: String?,
        val servings: String?,
        val ingredients: List<Ingredient>,
        val steps: List<Step>,
    )

    private fun wprm(doc: Document): Plugin? {
        val root =
            doc.selectFirst("[class*=wprm-recipe-container], .wprm-recipe") ?: doc.selectFirst("[class^=wprm-recipe-]")
                ?: return null
        val ingredients = root.select("li.wprm-recipe-ingredient").mapNotNull { li ->
            val group = li.closest(".wprm-recipe-ingredient-group")?.selectFirst(".wprm-recipe-group-name")?.text()
            ingredient(li.text(), group)
        }
        val steps = root.select(".wprm-recipe-instruction-text").flatMap { el ->
            val group = el.closest(".wprm-recipe-instruction-group")?.selectFirst(".wprm-recipe-group-name")?.text()
            steps(RecipeText.blockText(el), group)
        }
        return Plugin(
            title = root.selectFirst(".wprm-recipe-name")?.text(),
            servings = root.selectFirst(".wprm-recipe-servings")?.text(),
            ingredients = ingredients,
            steps = steps,
        )
    }

    private fun tasty(doc: Document): Plugin? {
        val root = doc.selectFirst(".tasty-recipes") ?: doc.selectFirst("[class^=tasty-recipes-]") ?: return null
        val ingredients = root.select(".tasty-recipes-ingredients li").mapNotNull { ingredient(it.text(), null) }
        val instructionItems = root.select(".tasty-recipes-instructions li")
            .ifEmpty { root.select(".tasty-recipes-instructions p") }
        return Plugin(
            title = root.selectFirst(".tasty-recipes-title, .tasty-recipes-header h2")?.text(),
            servings = root.selectFirst(".tasty-recipes-yield")?.text(),
            ingredients = ingredients,
            steps = instructionItems.flatMap { steps(RecipeText.blockText(it), null) },
        )
    }

    private fun ingredient(text: String, group: String?): Ingredient? =
        RecipeText.clean(text, RecipeLimits.INGREDIENT)?.let {
            IngredientParser.parse(it, RecipeText.clean(group, RecipeLimits.TITLE))
        }

    private fun steps(text: String, group: String?): List<Step> = RecipeText.lines(text, RecipeLimits.STEP)
        .map { Step(RecipeText.stripStepNumber(it), RecipeText.clean(group, RecipeLimits.TITLE)) }
}
