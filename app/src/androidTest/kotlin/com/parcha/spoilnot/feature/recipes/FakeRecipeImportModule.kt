package com.parcha.spoilnot.feature.recipes

import com.parcha.spoilnot.feature.recipes.data.RecipeImportModule
import com.parcha.spoilnot.feature.recipes.domain.Extraction
import com.parcha.spoilnot.feature.recipes.domain.ImportResult
import com.parcha.spoilnot.feature.recipes.domain.RecipeContent
import com.parcha.spoilnot.feature.recipes.domain.RecipeExtractor
import com.parcha.spoilnot.feature.recipes.domain.RecipeImportService
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton

/** Instrumented tests import from fixed pages instead of the network. */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [RecipeImportModule::class])
object FakeRecipeImportModule {
    const val PANCAKES_URL = "https://example.com/pancakes"

    val PANCAKES_PAGE = """<html><head><script type="application/ld+json">
        {"@context":"https://schema.org","@type":"Recipe","name":"Test pancakes","recipeYield":"2 servings",
         "totalTime":"PT20M","recipeIngredient":["1 cup flour","2 eggs","1 cup milk"],
         "recipeInstructions":[{"@type":"HowToStep","text":"Whisk."},{"@type":"HowToStep","text":"Fry."}]}
        </script></head></html>"""

    @Provides
    @Singleton
    fun importer(): RecipeImportService = object : RecipeImportService {
        override suspend fun fromUrl(url: String): ImportResult =
            when (val e = RecipeExtractor.fromHtml(url, if (url.startsWith(PANCAKES_URL)) PANCAKES_PAGE else "")) {
                is Extraction.Found -> ImportResult.Found(e.content, e.method)
                is Extraction.NotFound -> ImportResult.TryBrowser(url, RecipeContent(title = "", sourceUrl = url))
            }
    }
}
