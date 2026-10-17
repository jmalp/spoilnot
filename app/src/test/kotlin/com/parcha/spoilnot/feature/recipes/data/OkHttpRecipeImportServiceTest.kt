package com.parcha.spoilnot.feature.recipes.data

import com.parcha.spoilnot.feature.recipes.domain.ExtractionMethod
import com.parcha.spoilnot.feature.recipes.domain.ImportResult
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The page fetch of DESIGN 11.2 against a local server. */
class OkHttpRecipeImportServiceTest {
    private val server = MockWebServer()
    private val service = OkHttpRecipeImportService(OkHttpClient(), upgradeToHttps = false)

    private val recipePage = """<html><head><script type="application/ld+json">
        {"@type":"Recipe","name":"Toast","recipeIngredient":["2 slices bread"],"recipeInstructions":"Toast it."}
        </script></head></html>"""

    @Before
    fun start() = server.start()

    @After
    fun stop() = server.close()

    private fun html(body: String, code: Int = 200) =
        MockResponse.Builder().code(code).addHeader("Content-Type", "text/html; charset=utf-8").body(body).build()

    @Test
    fun `a page with a recipe, after a redirect, with a browser-like User-Agent`() = runTest {
        server.enqueue(MockResponse.Builder().code(301).addHeader("Location", "/final?utm_source=x").build())
        server.enqueue(html(recipePage))
        val result = service.fromUrl(server.url("/start").toString())
        assertTrue("$result", result is ImportResult.Found)
        result as ImportResult.Found
        assertEquals("Toast", result.content.title)
        assertEquals(ExtractionMethod.JsonLd, result.method)
        assertEquals(server.url("/final").toString(), result.content.sourceUrl)
        val request = server.takeRequest()
        assertTrue(request.headers["User-Agent"]!!.contains("Mozilla/5.0"))
    }

    @Test
    fun `blocked, missing and non-HTML pages go to the built-in browser`() = runTest {
        server.enqueue(html("<title>Just a moment...</title>", code = 403))
        server.enqueue(html("Not found", code = 404))
        server.enqueue(MockResponse.Builder().addHeader("Content-Type", "application/pdf").body("%PDF").build())
        server.enqueue(html("<html><head><meta property=\"og:title\" content=\"Tips\"></head></html>"))
        repeat(3) { assertTrue(service.fromUrl(server.url("/r$it").toString()) is ImportResult.TryBrowser) }
        val noRecipe = service.fromUrl(server.url("/tips").toString()) as ImportResult.TryBrowser
        assertEquals("Tips", noRecipe.prefill.title)
    }

    @Test
    fun `pages over 3 MB are cut and still read`() = runTest {
        server.enqueue(html(recipePage + "<p>" + "x".repeat(4 * 1024 * 1024) + "</p>"))
        assertTrue(service.fromUrl(server.url("/big").toString()) is ImportResult.Found)
    }

    @Test
    fun `only http and https links`() = runTest {
        assertEquals(ImportResult.InvalidUrl, service.fromUrl("file:///sdcard/page.html"))
        assertEquals(ImportResult.InvalidUrl, service.fromUrl("not a link"))
    }

    @Test
    fun `an unreachable host is offline`() = runTest {
        server.close()
        assertEquals(ImportResult.Offline, service.fromUrl(server.url("/gone").toString()))
    }
}
