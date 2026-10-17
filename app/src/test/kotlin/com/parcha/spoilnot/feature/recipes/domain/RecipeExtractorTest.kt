package com.parcha.spoilnot.feature.recipes.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Synthetic fixtures for every JSON-LD shape in DESIGN 11.3, plus microdata and heuristics (11.4). */
class RecipeExtractorTest {
    private fun fixture(name: String): String =
        checkNotNull(javaClass.getResource("/recipes/fixtures/$name")) { name }.readText()

    private fun found(
        name: String,
        url: String = "https://example.com/recipes/test?utm_source=feed",
    ): Extraction.Found {
        val result = RecipeExtractor.fromHtml(url, fixture(name))
        assertTrue("$name: $result", result is Extraction.Found)
        return result as Extraction.Found
    }

    @Test
    fun `a top-level object with a string image and string instructions`() {
        val r = found("jsonld-object.html")
        val c = r.content
        assertEquals(ExtractionMethod.JsonLd, r.method)
        assertEquals("Lemon & Herb Rice", c.title)
        assertEquals("https://example.com/images/lemon-rice.jpg", c.imageUrl)
        assertEquals("A bright side dish.", c.description)
        assertEquals("Sam Cook", c.sourceName)
        assertEquals(4, c.servings)
        assertEquals("4 servings", c.servingsText)
        assertEquals(10, c.prepMinutes)
        assertEquals(20, c.cookMinutes)
        assertEquals(30, c.totalMinutes)
        assertEquals(4, c.ingredients.size)
        assertEquals(1.5, c.ingredients[0].qty!!, 1e-9)
        assertEquals("cup", c.ingredients[0].unit)
        assertEquals(
            listOf(
                "Rinse the rice.",
                "Melt the butter and toast the rice.",
                "Add water and simmer.",
                "Stir in the lemon.",
            ),
            c.steps.map { it.text },
        )
        assertEquals(listOf("Side dish", "Greek", "rice", "side", "lemon"), c.tags)
        assertEquals("https://example.com/recipes/test", c.sourceUrl)
        assertEquals(ImportMethod.Web, c.importMethod)
    }

    @Test
    fun `a top-level array with an ImageObject and a list of strings`() {
        val c = found("jsonld-array.html").content
        assertEquals("Tomato Soup", c.title)
        assertEquals("https://cdn.example.com/soup.jpg", c.imageUrl)
        assertEquals(6, c.servings)
        assertEquals("6 bowls", c.servingsText)
        // Total missing: prep plus cook.
        assertEquals(75, c.totalMinutes)
        assertEquals(
            listOf("Soften the onion.", "Add tomatoes and stock.", "Blend until smooth."),
            c.steps.map {
                it.text
            },
        )
        assertEquals("can", c.ingredients[0].unit)
        assertEquals("Alex Chef", c.sourceName)
    }

    @Test
    fun `a graph with a type list, image list, author list and HowToSteps`() {
        val c = found("jsonld-graph.html").content
        assertEquals("Fluffy Pancakes", c.title)
        assertEquals("https://example.com/p-1x1.jpg", c.imageUrl)
        assertEquals("Jo Baker", c.sourceName)
        assertEquals(8, c.servings)
        assertEquals(25, c.totalMinutes)
        assertEquals(
            listOf(
                "Whisk the dry ingredients.",
                "Add eggs and milk and whisk until smooth.",
                "Cook the pancakes in a hot pan.",
            ),
            c.steps.map { it.text },
        )
        assertEquals(listOf("breakfast", "pancakes"), c.tags)
        assertEquals("g", c.ingredients[0].unit)
    }

    @Test
    fun `HowToSection names become step groups`() {
        val c = found("jsonld-sections.html").content
        assertEquals(
            listOf("For the curry", "For the curry", "For the rice"),
            c.steps.map { it.group },
        )
        assertEquals("Cook the rice.", c.steps.last().text)
    }

    @Test
    fun `bad JSON with a BOM, comments, trailing commas and raw line breaks`() {
        val c = found("jsonld-broken.html").content
        assertEquals("Garlic Bread", c.title)
        assertEquals(3, c.ingredients.size)
        assertEquals("clove", c.ingredients[1].unit)
        assertEquals(
            listOf("Mix the garlic and butter.", "Spread on the bread. Bake for 10 minutes."),
            c.steps.map {
                it.text
            },
        )
        assertEquals(15, c.totalMinutes)
    }

    @Test
    fun `several recipes on a page, the one with the most ingredients wins`() {
        val c = found("jsonld-multiple.html").content
        assertEquals("Chocolate Chip Cookies", c.title)
        assertEquals("Cream the butter & sugar.", c.steps.first().text)
        // No image in the JSON-LD: og:image.
        assertEquals("https://example.com/og-cookies.jpg", c.imageUrl)
        assertEquals(27, c.totalMinutes)
    }

    @Test
    fun `microdata through the same mapper`() {
        val r = found("microdata.html")
        val c = r.content
        assertEquals(ExtractionMethod.Microdata, r.method)
        assertEquals("Banana Bread", c.title)
        assertEquals("https://example.com/img/banana-bread.jpg", c.imageUrl)
        assertEquals("Pat Oven", c.sourceName)
        assertEquals(75, c.totalMinutes)
        assertEquals(1, c.servings)
        assertEquals(3, c.ingredients.size)
        assertEquals(listOf("Heat the oven to 180°C.", "Mix everything.", "Bake for an hour."), c.steps.map { it.text })
    }

    @Test
    fun `WP Recipe Maker markup without JSON-LD`() {
        val r = found("heuristic-wprm.html")
        val c = r.content
        assertEquals(ExtractionMethod.Heuristic, r.method)
        assertEquals("Weeknight Chili", c.title)
        assertEquals("https://example.com/chili.jpg", c.imageUrl)
        assertEquals(6, c.servings)
        assertEquals(listOf("Chili", "Chili", "Toppings"), c.ingredients.map { it.group })
        assertEquals(listOf("Brown the beef.", "Add beans and simmer 20 minutes."), c.steps.map { it.text })
    }

    @Test
    fun `Tasty Recipes markup without JSON-LD`() {
        val r = found("heuristic-tasty.html")
        assertEquals(ExtractionMethod.Heuristic, r.method)
        assertEquals("Overnight Oats", r.content.title)
        assertEquals(3, r.content.ingredients.size)
        assertEquals(2, r.content.steps.size)
        assertEquals("2 jars", r.content.servingsText)
    }

    @Test
    fun `a page without a recipe prefills Create manually`() {
        val result = RecipeExtractor.fromHtml("https://example.com/tips?utm_campaign=x", fixture("no-recipe.html"))
        assertEquals(
            Extraction.NotFound(
                RecipeContent(
                    title = "10 Kitchen Tips",
                    sourceUrl = "https://example.com/tips",
                    imageUrl = "https://example.com/tips.jpg",
                ),
            ),
            result,
        )
    }

    @Test
    fun `imported text is capped (DESIGN 11-6)`() {
        val long = "x".repeat(500)
        val ingredients = (1..150).joinToString(",") { "\"$it cup $long\"" }
        val steps = (1..150).joinToString(",") { "\"Step $long\"" }
        val html = """<script type="application/ld+json">{"@type":"Recipe","name":"$long",
            "recipeIngredient":[$ingredients],"recipeInstructions":[$steps]}</script>"""
        val c = (RecipeExtractor.fromHtml("https://example.com/", html) as Extraction.Found).content
        assertEquals(RecipeLimits.TITLE, c.title.length)
        assertEquals(RecipeLimits.MAX_INGREDIENTS, c.ingredients.size)
        assertEquals(RecipeLimits.MAX_STEPS, c.steps.size)
        assertTrue(c.ingredients.all { it.raw.length <= RecipeLimits.INGREDIENT })
    }

    @Test
    fun `the browser snapshot uses JSON-LD from the live page`() {
        val page = PageSnapshot(
            url = "https://example.com/r/1#top",
            title = "Page title",
            ld = listOf(
                fixture(
                    "jsonld-object.html",
                ).substringAfter("<script type=\"application/ld+json\">").substringBefore("</script>"),
            ),
            ogImage = null,
        )
        val r = RecipeExtractor.fromPage(page) as Extraction.Found
        assertEquals("Lemon & Herb Rice", r.content.title)
        assertEquals("https://example.com/r/1", r.content.sourceUrl)
    }

    @Test
    fun `the browser snapshot falls back to the serialized page`() {
        val page =
            PageSnapshot("https://example.com/chili", "Chili", emptyList(), null, html = fixture("heuristic-wprm.html"))
        assertEquals(ExtractionMethod.Heuristic, (RecipeExtractor.fromPage(page) as Extraction.Found).method)
        val empty = RecipeExtractor.fromPage(PageSnapshot("https://example.com/", "Home", emptyList(), "/og.jpg"))
        assertEquals(
            Extraction.NotFound(
                RecipeContent(
                    title = "Home",
                    sourceUrl = "https://example.com/",
                    imageUrl = "https://example.com/og.jpg",
                ),
            ),
            empty,
        )
    }

    @Test
    fun `script results from evaluateJavascript`() {
        val inner = """{"url":"https://example.com/a","title":"A","ld":["{}"],"ogImage":null,"html":null}"""
        val quoted = kotlinx.serialization.json.JsonPrimitive(inner).toString()
        val page = BrowserScript.parse(quoted)!!
        assertEquals("https://example.com/a", page.url)
        assertEquals(listOf("{}"), page.ld)
        assertNull(page.html)
        assertNull(BrowserScript.parse("null"))
        assertNull(BrowserScript.parse("\"not json\""))
    }
}

/**
 * Trimmed structure snapshots of the 10 sites in docs/import-test-sites.md: the real JSON-LD shape with all text
 * and links replaced by synthetic placeholders (DESIGN 11.6, no copyrighted text).
 */
class SiteSnapshotTest {
    private data class Expected(
        val ingredients: Int,
        val steps: Int,
        val groups: Int,
        val servings: Int?,
        val total: Int?,
        val prep: Int?,
        val cook: Int?,
    )

    private val sites = mapOf(
        "allrecipes" to Expected(11, 9, 0, 48, 30, 20, 10),
        "bbcgoodfood" to Expected(6, 5, 0, 12, 30, 10, 20),
        "bonappetit" to Expected(10, 6, 0, 16, 40, null, 20),
        "budgetbytes" to Expected(17, 6, 0, 4, 30, 10, 20),
        "foodnetwork" to Expected(11, 6, 0, 2, 110, 20, 30),
        "kingarthurbaking" to Expected(7, 7, 0, 16, 162, 12, 40),
        "minimalistbaker" to Expected(12, 10, 0, 4, 75, 15, 60),
        "recipetineats" to Expected(19, 10, 4, 4, 60, 15, 45),
        "seriouseats" to Expected(11, 6, 0, 28, 75, 20, 30),
        "simplyrecipes" to Expected(25, 13, 2, 6, 150, 30, 30),
    )

    @Test
    fun `every site snapshot imports with the right shape`() {
        assertEquals(10, sites.size)
        for ((site, expected) in sites) {
            val html = checkNotNull(javaClass.getResource("/recipes/snapshots/$site.html")) { site }.readText()
            val result = RecipeExtractor.fromHtml("https://www.$site.com/recipe", html)
            assertTrue("$site: $result", result is Extraction.Found)
            val c = (result as Extraction.Found).content
            assertEquals(site, ExtractionMethod.JsonLd, result.method)
            assertEquals(site, "Synthetic recipe title", c.title)
            assertTrue("$site image", c.imageUrl?.startsWith("https://example.com/") == true)
            assertEquals("$site ingredients", expected.ingredients, c.ingredients.size)
            assertEquals("$site steps", expected.steps, c.steps.size)
            assertEquals("$site groups", expected.groups, c.steps.mapNotNull { it.group }.distinct().size)
            assertEquals("$site servings", expected.servings, c.servings)
            assertEquals("$site total", expected.total, c.totalMinutes)
            assertEquals("$site prep", expected.prep, c.prepMinutes)
            assertEquals("$site cook", expected.cook, c.cookMinutes)
            assertTrue("$site parsed", c.ingredients.count { it.qty != null } >= expected.ingredients / 2)
            assertTrue("$site tags", c.tags.size <= RecipeLimits.MAX_TAGS)
        }
    }
}
