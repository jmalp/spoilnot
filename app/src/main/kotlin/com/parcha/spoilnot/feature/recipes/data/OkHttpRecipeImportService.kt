package com.parcha.spoilnot.feature.recipes.data

import com.parcha.spoilnot.feature.recipes.domain.Extraction
import com.parcha.spoilnot.feature.recipes.domain.ImportResult
import com.parcha.spoilnot.feature.recipes.domain.RecipeContent
import com.parcha.spoilnot.feature.recipes.domain.RecipeExtractor
import com.parcha.spoilnot.feature.recipes.domain.RecipeImportService
import com.parcha.spoilnot.feature.recipes.domain.SourceUrl
import java.io.IOException
import java.net.ConnectException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.coroutines.executeAsync

/**
 * Fetches a recipe page (DESIGN 11.2): 15 s timeout, 3 MB cap, browser-like User-Agent, redirects followed,
 * http or https only. Anything other than a page with a recipe goes on to the built-in browser.
 */
@Singleton
class OkHttpRecipeImportService internal constructor(
    client: OkHttpClient,
    /** Off only in JVM tests against a local plain-http server. */
    private val upgradeToHttps: Boolean,
) : RecipeImportService {
    @Inject constructor(client: OkHttpClient) : this(client, upgradeToHttps = true)

    private val http = client.newBuilder()
        .callTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    override suspend fun fromUrl(url: String): ImportResult {
        val canonical = SourceUrl.canonical(url) ?: return ImportResult.InvalidUrl
        val request = try {
            Request.Builder()
                .url(if (upgradeToHttps) SourceUrl.preferHttps(canonical) else canonical)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9")
                .build()
        } catch (e: IllegalArgumentException) {
            return ImportResult.InvalidUrl
        }
        val fallback = ImportResult.TryBrowser(canonical, RecipeContent(title = "", sourceUrl = canonical))
        val page = try {
            http.newCall(request).executeAsync().use { response ->
                val type = response.body.contentType()
                val isHtml = type == null || type.subtype.contains("html") || type.subtype == "xml"
                if (!response.isSuccessful || !isHtml) return fallback
                val finalUrl = response.request.url.toString()
                withContext(Dispatchers.IO) {
                    val source = response.body.source()
                    source.request(MAX_BYTES)
                    val bytes = source.buffer.readByteArray(minOf(source.buffer.size, MAX_BYTES))
                    finalUrl to String(bytes, type?.charset() ?: Charsets.UTF_8)
                }
            }
        } catch (e: UnknownHostException) {
            return ImportResult.Offline
        } catch (e: ConnectException) {
            return ImportResult.Offline
        } catch (e: IOException) {
            // Timeouts, resets and TLS problems: the live page may still work.
            return fallback
        }
        val (finalUrl, html) = page
        if (html.isBlank()) return fallback
        return when (val extraction = withContext(Dispatchers.Default) { RecipeExtractor.fromHtml(finalUrl, html) }) {
            is Extraction.Found -> ImportResult.Found(extraction.content, extraction.method)
            is Extraction.NotFound -> ImportResult.TryBrowser(finalUrl, extraction.prefill)
        }
    }

    private companion object {
        const val TIMEOUT_SECONDS = 15L
        const val MAX_BYTES = 3L * 1024 * 1024
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 16; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/141.0.0.0 Mobile Safari/537.36"
    }
}
