package com.parcha.spoilnot.feature.recipes.domain

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** How much of a page's JSON-LD the import reads, in the built-in browser and in a fetched page (after v1). */
class JsonLdLimitsTest {
    private val recipe =
        """{"@type":"Recipe","name":"Pancakes","recipeIngredient":["2 eggs","1 cup milk"],""" +
            """"recipeInstructions":["Whisk.","Fry."]}"""

    /** A valid JSON-LD block of exactly [chars] characters. */
    private fun block(chars: Int): String {
        val head = """{"@type":"Thing","description":""""
        val tail = "\"}"
        return head + "x".repeat(chars - head.length - tail.length) + tail
    }

    /** A recipe with more ingredients than [recipe], in a block just over the per-block cap. */
    private fun bigRecipe(): String {
        val head = """{"@type":"Recipe","name":"Big","recipeIngredient":["1 a","2 b","3 c"],""" +
            """"recipeInstructions":["Mix."],"description":""""
        val tail = "\"}"
        return head + "x".repeat(JsonLdLimits.MAX_BLOCK_CHARS + 1 - head.length - tail.length) + tail
    }

    /** What evaluateJavascript hands the app: the script's JSON, as a JSON string literal. */
    private fun scriptResult(ld: List<String>, html: String? = null): String {
        val page = JsonObject(
            mapOf(
                "url" to JsonPrimitive("https://example.com/pancakes"),
                "title" to JsonPrimitive("Pancakes"),
                "ld" to JsonArray(ld.map(::JsonPrimitive)),
                "ogImage" to JsonNull,
                "html" to (html?.let(::JsonPrimitive) ?: JsonNull),
            ),
        )
        return JsonPrimitive(page.toString()).toString()
    }

    @Test
    fun `a page with many blocks is read up to the block limit, in page order`() {
        val blocks = List(500) { """{"@type":"Thing","name":"n$it"}""" }
        val kept = JsonLdLimits.cap(blocks)
        assertEquals(JsonLdLimits.MAX_BLOCKS, kept.size)
        assertEquals(blocks.take(JsonLdLimits.MAX_BLOCKS), kept)
    }

    @Test
    fun `an oversized block is left out and a later recipe still found`() {
        val huge = block(JsonLdLimits.MAX_BLOCK_CHARS + 1)
        assertEquals(listOf(recipe), JsonLdLimits.cap(listOf(huge, recipe)))
        assertEquals(
            listOf(block(JsonLdLimits.MAX_BLOCK_CHARS)),
            JsonLdLimits.cap(listOf(block(JsonLdLimits.MAX_BLOCK_CHARS))),
        )
    }

    @Test
    fun `blocks that would pass the total are left out`() {
        val big = block(900_000)
        val small = block(100_000)
        val kept = JsonLdLimits.cap(listOf(big, big, big, big, small, block(400_000)))
        // 3 × 900,000 + 100,000 = 2.8 M; a fourth big block (3.6 M) or the last one (3.2 M) would pass 3 MiB.
        assertEquals(listOf(big, big, big, small), kept)
        assertTrue(kept.sumOf { it.length } <= JsonLdLimits.MAX_TOTAL_CHARS)
    }

    @Test
    fun `the browser's result keeps the caps even if the page changed what the script returned`() {
        // Without the caps the big recipe would win: it has more ingredients.
        val page = BrowserScript.parse(scriptResult(List(3) { bigRecipe() } + recipe))!!
        assertEquals(listOf(recipe), page.ld)
        assertEquals("Pancakes", (RecipeExtractor.fromPage(page) as Extraction.Found).content.title)

        val many = BrowserScript.parse(scriptResult(List(100) { """{"@type":"Thing","name":"n$it"}""" }))!!
        assertEquals(JsonLdLimits.MAX_BLOCKS, many.ld.size)
    }

    @Test
    fun `an oversized browser result is not read at all`() {
        val ld =
            List(BrowserScript.MAX_RESULT / JsonLdLimits.MAX_BLOCK_CHARS + 1) { block(JsonLdLimits.MAX_BLOCK_CHARS) }
        val result = scriptResult(ld)
        assertTrue(result.length > BrowserScript.MAX_RESULT)
        assertNull(BrowserScript.parse(result))
    }

    @Test
    fun `the browser's page markup is cut at 3 MB`() {
        val html = "<p>" + "x".repeat(BrowserScript.MAX_HTML_CHARS) + "</p>"
        val page = BrowserScript.parse(scriptResult(emptyList(), html))!!
        assertEquals(BrowserScript.MAX_HTML_CHARS, page.html!!.length)
    }

    @Test
    fun `a fetched page reads its JSON-LD with the same caps`() {
        val html = buildString {
            append("<html><head><title>Pancakes</title>")
            repeat(3) { append("<script type=\"application/ld+json\">${bigRecipe()}</script>") }
            append("<script type=\"application/ld+json\">$recipe</script>")
            append("</head><body></body></html>")
        }
        val found = RecipeExtractor.fromHtml("https://example.com/pancakes", html)
        assertEquals(listOf("2 eggs", "1 cup milk"), (found as Extraction.Found).content.ingredients.map { it.raw })
    }
}
