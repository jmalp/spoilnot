package com.parcha.spoilnot.feature.recipes

import android.util.Log
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.parcha.spoilnot.HiltTestActivity
import com.parcha.spoilnot.feature.recipes.data.OkHttpRecipeImportService
import com.parcha.spoilnot.feature.recipes.domain.BrowserScript
import com.parcha.spoilnot.feature.recipes.domain.Extraction
import com.parcha.spoilnot.feature.recipes.domain.ImportResult
import com.parcha.spoilnot.feature.recipes.domain.RecipeContent
import com.parcha.spoilnot.feature.recipes.domain.RecipeExtractor
import com.parcha.spoilnot.feature.recipes.ui.RecipeWebView
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * M5 acceptance against the real sites in docs/import-test-sites.md: the app's fetch first, and for blocked pages
 * the app's locked-down web view, as in DESIGN 11.2. Needs the internet, so it runs only when asked:
 * `./gradlew connectedDevDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.live=true
 *  -Pandroid.testInstrumentationRunnerArguments.class=com.parcha.spoilnot.feature.recipes.LiveImportTest`
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class LiveImportTest {
    @get:Rule
    val hilt = HiltAndroidRule(this)

    private val sites = listOf(
        "https://www.allrecipes.com/recipe/10813/best-chocolate-chip-cookies/",
        "https://www.bbcgoodfood.com/recipes/easy-pancakes",
        "https://www.seriouseats.com/the-food-lab-best-chocolate-chip-cookie-recipe",
        "https://www.budgetbytes.com/one-pot-creamy-cajun-chicken-pasta/",
        "https://www.recipetineats.com/chicken-chasseur/",
        "https://www.simplyrecipes.com/recipes/homemade_pizza/",
        "https://www.foodnetwork.com/recipes/alton-brown/the-chewy-recipe-1909046",
        "https://www.bonappetit.com/recipe/bas-best-chocolate-chip-cookies",
        "https://www.kingarthurbaking.com/recipes/classic-sandwich-bread-recipe",
        "https://minimalistbaker.com/easy-vegan-fried-rice/",
    )

    private data class Outcome(val path: String, val content: RecipeContent?, val ms: Long)

    @Test
    fun importsAtLeastNineOfTheTenSites() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue("live import runs only with -e live true", args.getString("live") == "true")
        val importer = OkHttpRecipeImportService(OkHttpClient())
        val outcomes = sites.associateWith { url ->
            val start = System.currentTimeMillis()
            when (val fetched = runBlocking { importer.fromUrl(url) }) {
                is ImportResult.Found -> Outcome("fetch", fetched.content, System.currentTimeMillis() - start)
                else -> Outcome("browser", inBrowser(url), System.currentTimeMillis() - start)
            }
        }
        outcomes.forEach { (url, o) ->
            val c = o.content
            Log.i(
                TAG,
                "$url | ${o.path} | ${o.ms} ms | " + if (c == null) {
                    "NOT FOUND"
                } else {
                    "title ${c.title.length} chars, image ${c.imageUrl != null}, ${c.ingredients.size} ingredients " +
                        "(${c.ingredients.count { it.qty != null }} parsed), ${c.steps.size} steps, " +
                        "servings ${c.servings}, total ${c.totalMinutes} min"
                },
            )
        }
        val found = outcomes.values.count { it.content != null }
        assertTrue("$found of ${sites.size} imported", found >= 9)
    }

    /** Loads the page in the app's web view and waits for the extraction script to find a recipe. */
    private fun inBrowser(url: String): RecipeContent? {
        val found = CompletableFuture<RecipeContent?>()
        ActivityScenario.launch(HiltTestActivity::class.java).use { scenario ->
            var view: RecipeWebView? = null
            scenario.onActivity { activity ->
                view = RecipeWebView(activity).also { web ->
                    web.listener = object : RecipeWebView.Listener {
                        override fun onPageStarted(url: String) = Unit

                        override fun onPageResult(result: String?, final: Boolean) {
                            val page = BrowserScript.parse(result) ?: return
                            val extracted = RecipeExtractor.fromPage(page)
                            if (extracted is Extraction.Found) found.complete(extracted.content)
                        }

                        override fun onHistory(canGoBackNow: Boolean, url: String) = Unit

                        override fun onProgress(percent: Int) = Unit
                    }
                    activity.setContentView(web)
                    web.loadUrl(url)
                }
            }
            val content = runCatching { found.get(BROWSER_TIMEOUT_S, TimeUnit.SECONDS) }.getOrNull()
            scenario.onActivity { view?.release() }
            return content
        }
    }

    private companion object {
        const val TAG = "LiveImportTest"
        const val BROWSER_TIMEOUT_S = 45L
    }
}
