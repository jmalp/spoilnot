package com.parcha.spoilnot.feature.recipes.ui

import android.content.Context
import com.parcha.spoilnot.R
import com.parcha.spoilnot.feature.recipes.domain.RecipeWords
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent

/** The strings.xml words recipe logic writes into scaled lines ([RecipeWords]), read when a screen is created. */
@Module
@InstallIn(SingletonComponent::class)
object RecipeWordsModule {
    @Provides
    fun recipeWords(@ApplicationContext context: Context): RecipeWords =
        RecipeWords(pinch = context.getString(R.string.recipe_scaled_pinch))
}
