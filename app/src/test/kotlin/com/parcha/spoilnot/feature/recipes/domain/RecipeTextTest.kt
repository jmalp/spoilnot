package com.parcha.spoilnot.feature.recipes.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecipeTextTest {
    @Test
    fun `clean strips tags, decodes entities and collapses whitespace`() {
        assertEquals("Mac & Cheese", RecipeText.clean("  <b>Mac</b> &amp;   Cheese ", 200))
        assertEquals("Fish & Chips", RecipeText.clean("Fish &amp;amp; Chips", 200))
        assertEquals("abc", RecipeText.clean("abcdef", 3))
        assertNull(RecipeText.clean("  <br> ", 10))
        assertNull(RecipeText.clean(null, 10))
    }

    @Test
    fun `lines splits on line breaks and HTML blocks`() {
        assertEquals(listOf("One", "Two"), RecipeText.lines("One\n\nTwo", 100))
        assertEquals(listOf("One", "Two", "Three"), RecipeText.lines("<p>One</p><p>Two<br>Three</p>", 100))
        assertEquals(listOf("A", "B"), RecipeText.lines("<ol><li>A</li><li>B</li></ol>", 100))
    }

    @Test
    fun `step numbers are removed`() {
        assertEquals("Preheat the oven.", RecipeText.stripStepNumber("1. Preheat the oven."))
        assertEquals("Mix.", RecipeText.stripStepNumber("Step 2: Mix."))
        assertEquals("Mix.", RecipeText.stripStepNumber("3) Mix."))
        assertEquals("350 degrees is hot.", RecipeText.stripStepNumber("350 degrees is hot."))
    }

    @Test
    fun `tags are split, de-duplicated keeping the first spelling, and capped at eight`() {
        assertEquals(listOf("Dinner", "quick"), RecipeText.tags(listOf("Dinner, quick", "dinner", "Quick")))
        assertEquals(8, RecipeText.tags((1..12).map { "tag$it" }).size)
    }
}

class DurationsTest {
    @Test
    fun `ISO 8601 durations`() {
        assertEquals(90, Durations.minutes("PT1H30M"))
        assertEquals(90, Durations.minutes("PT90M"))
        assertEquals(30, Durations.minutes("P0DT0H30M"))
        assertEquals(30, Durations.minutes("P0Y0M0DT0H30M0.000S"))
        assertEquals(1440 + 60, Durations.minutes("P1DT1H"))
        assertEquals(30, Durations.minutes("PT0.5H"))
        assertEquals(1, Durations.minutes("PT45S"))
    }

    @Test
    fun `plain-text times`() {
        assertEquals(20, Durations.minutes("20 minutes"))
        assertEquals(75, Durations.minutes("1 hour 15 mins"))
        assertEquals(120, Durations.minutes("2 hrs"))
        assertEquals(45, Durations.minutes("45"))
    }

    @Test
    fun `zero, blank and nonsense are unknown`() {
        assertNull(Durations.minutes("PT0M"))
        assertNull(Durations.minutes(""))
        assertNull(Durations.minutes(null))
        assertNull(Durations.minutes("about a while"))
    }
}

class YieldTest {
    @Test
    fun `first number and the wording`() {
        assertEquals(4 to "4 servings", Yield.of(listOf("4", "4 servings")))
        assertEquals(12 to "Makes 12 cookies", Yield.of(listOf("Makes 12 cookies")))
        assertEquals(4 to "4-6", Yield.of(listOf("4-6")))
        assertEquals(null to "One loaf", Yield.of(listOf("One loaf")))
        assertEquals(null to null, Yield.of(emptyList()))
    }
}

class SourceUrlTest {
    @Test
    fun `canonical drops tracking parameters, the fragment and default ports`() {
        assertEquals(
            "https://example.com/pie/?id=3",
            SourceUrl.canonical("HTTPS://Example.COM:443/pie/?utm_source=x&id=3&utm_medium=y&fbclid=z#comments"),
        )
        assertEquals("https://example.com/", SourceUrl.canonical("https://example.com"))
        assertEquals("http://example.com:8080/a", SourceUrl.canonical("http://example.com:8080/a"))
    }

    @Test
    fun `only http and https`() {
        assertNull(SourceUrl.canonical("javascript:alert(1)"))
        assertNull(SourceUrl.canonical("file:///etc/hosts"))
        assertNull(SourceUrl.canonical("content://media/1"))
        assertNull(SourceUrl.canonical("intent://x#Intent;end"))
        assertNull(SourceUrl.canonical("example.com/pie"))
        assertNull(SourceUrl.canonical("not a url"))
    }

    @Test
    fun `same key ignores www, scheme and trailing slash`() {
        assertEquals(
            SourceUrl.sameKey("https://www.example.com/pie/"),
            SourceUrl.sameKey("http://example.com/pie?utm_x=1"),
        )
    }

    @Test
    fun `finds the link in shared text`() {
        assertEquals(
            "https://example.com/pie",
            SourceUrl.fromSharedText("Look at this pie! https://example.com/pie."),
        )
        assertNull(SourceUrl.fromSharedText("no link here"))
    }

    @Test
    fun `pasted text and the address bar`() {
        assertEquals("https://example.com/pie", SourceUrl.fromPasted("example.com/pie"))
        assertNull(SourceUrl.fromPasted("banana bread"))
        assertEquals("https://example.com/pie", SourceUrl.fromAddressBar("example.com/pie"))
        assertEquals("https://www.google.com/search?q=banana+bread", SourceUrl.fromAddressBar("banana bread"))
    }

    @Test
    fun `relative images resolve against the page`() {
        assertEquals("https://example.com/img/a.jpg", SourceUrl.resolve("https://example.com/r/pie", "/img/a.jpg"))
        assertEquals(
            "https://cdn.example.com/a.jpg",
            SourceUrl.resolve("https://example.com/r/pie", "//cdn.example.com/a.jpg"),
        )
        assertNull(SourceUrl.resolve("https://example.com/", "data:image/png;base64,AAAA"))
    }

    @Test
    fun `http becomes https for fetching`() {
        assertEquals("https://example.com/a", SourceUrl.preferHttps("http://example.com/a"))
        assertEquals("https://example.com/a", SourceUrl.preferHttps("https://example.com/a"))
    }
}
