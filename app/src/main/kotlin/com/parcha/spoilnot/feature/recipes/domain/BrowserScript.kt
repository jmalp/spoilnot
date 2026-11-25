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
 *
 * The page's JSON-LD is capped as in [JsonLdLimits] (after v1): the script leaves out a block over the per-block
 * cap or one that would pass the total, and [parse] applies the same caps again, because the page's own scripts can
 * change what the script sees. [parse] refuses a result over [MAX_RESULT] characters outright.
 */
object BrowserScript {
    val SOURCE = """(function () {
  var ld = [], total = 0, scripts = document.scripts;
  for (var i = 0; i < scripts.length && ld.length < ${JsonLdLimits.MAX_BLOCKS}; i++) {
    var s = scripts[i];
    if ((s.type || '').toLowerCase() !== 'application/ld+json') continue;
    var t = s.textContent || '';
    if (t.length > ${JsonLdLimits.MAX_BLOCK_CHARS} || total + t.length > ${JsonLdLimits.MAX_TOTAL_CHARS}) continue;
    total += t.length;
    ld.push(t);
  }
  var og = Array.prototype.slice.call(document.getElementsByTagName('meta'))
    .find(function (m) { return m.getAttribute('property') === 'og:image'; });
  var hasRecipe = ld.some(function (t) { return t.indexOf('Recipe') >= 0; });
  return JSON.stringify({
    url: location.href,
    title: document.title,
    ld: ld,
    ogImage: og ? og.content : null,
    html: hasRecipe ? null : document.documentElement.outerHTML.slice(0, $MAX_HTML_CHARS)
  });
})()"""

    /** The serialized DOM the script sends when no JSON-LD mentions a Recipe: 3 MB, like a fetched page. */
    const val MAX_HTML_CHARS = 3 * 1024 * 1024

    /**
     * The longest result [parse] reads, as evaluateJavascript returns it (escaped twice). A real page's capped
     * JSON-LD and DOM stay well under it; anything longer is left unread.
     */
    const val MAX_RESULT = 16 * 1024 * 1024

    private val json = Json { ignoreUnknownKeys = true }

    /** evaluateJavascript returns the script's string result as a JSON string literal. Not for the main thread. */
    fun parse(result: String?): PageSnapshot? {
        if (result.isNullOrEmpty() || result == "null" || result.length > MAX_RESULT) return null
        return runCatching {
            val text = json.parseToJsonElement(result).jsonPrimitive.content
            val page = json.parseToJsonElement(text) as JsonObject
            PageSnapshot(
                url = page.string("url") ?: return null,
                title = page.string("title"),
                ld = JsonLdLimits.cap(
                    (page["ld"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty(),
                ),
                ogImage = page.string("ogImage"),
                html = page.string("html")?.take(MAX_HTML_CHARS),
            )
        }.getOrNull()
    }

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
}

/**
 * How much of a page's JSON-LD the import reads (after v1), in the built-in browser and in a fetched page alike.
 * A recipe page's JSON-LD is a few to a few hundred KB in one to a handful of blocks; these leave plenty of room.
 */
object JsonLdLimits {
    /** Blocks read per page; later ones are left out. */
    const val MAX_BLOCKS = 32

    /** A longer block is left out (cutting it would only make it invalid JSON). */
    const val MAX_BLOCK_CHARS = 1024 * 1024

    /** All blocks read from a page together; a block that would pass it is left out. */
    const val MAX_TOTAL_CHARS = 3 * 1024 * 1024

    /** The blocks to read, in page order: as the browser script picks them. */
    fun cap(blocks: List<String>): List<String> {
        val kept = ArrayList<String>(minOf(blocks.size, MAX_BLOCKS))
        var total = 0
        for (block in blocks) {
            if (kept.size == MAX_BLOCKS) break
            if (block.length > MAX_BLOCK_CHARS || total + block.length > MAX_TOTAL_CHARS) continue
            total += block.length
            kept += block
        }
        return kept
    }
}
