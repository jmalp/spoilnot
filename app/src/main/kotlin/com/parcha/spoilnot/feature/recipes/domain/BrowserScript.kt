package com.parcha.spoilnot.feature.recipes.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * The script the built-in browser runs after each page load, read back with evaluateJavascript (DESIGN 11.2).
 * Besides the design's fields it returns the serialized DOM, capped at 3 MB, when no JSON-LD mentions a Recipe, so
 * microdata and recipe-plugin markup work in the browser too (deviation, see docs/DECISIONS.md).
 */
object BrowserScript {
    const val SOURCE = """(function () {
  var ld = Array.prototype.slice.call(document.scripts)
    .filter(function (s) { return (s.type || '').toLowerCase() === 'application/ld+json'; })
    .map(function (s) { return s.textContent; });
  var og = Array.prototype.slice.call(document.getElementsByTagName('meta'))
    .find(function (m) { return m.getAttribute('property') === 'og:image'; });
  var hasRecipe = ld.some(function (t) { return t.indexOf('Recipe') >= 0; });
  return JSON.stringify({
    url: location.href,
    title: document.title,
    ld: ld,
    ogImage: og ? og.content : null,
    html: hasRecipe ? null : document.documentElement.outerHTML.slice(0, 3145728)
  });
})()"""

    private val json = Json { ignoreUnknownKeys = true }

    /** evaluateJavascript returns the script's string result as a JSON string literal. */
    fun parse(result: String?): PageSnapshot? {
        if (result.isNullOrEmpty() || result == "null") return null
        return runCatching {
            val text = json.parseToJsonElement(result).jsonPrimitive.content
            val page = json.parseToJsonElement(text) as JsonObject
            PageSnapshot(
                url = page.string("url") ?: return null,
                title = page.string("title"),
                ld = (page["ld"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty(),
                ogImage = page.string("ogImage"),
                html = page.string("html"),
            )
        }.getOrNull()
    }

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
}
