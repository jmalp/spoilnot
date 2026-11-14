package com.parcha.spoilnot

import androidx.test.platform.app.InstrumentationRegistry
import com.parcha.spoilnot.feature.recipes.domain.RecipeWords
import com.parcha.spoilnot.feature.recipes.ui.RecipeWordsModule

/** strings.xml's recipe words, as the app provides them to its ViewModels. */
fun recipeWords(): RecipeWords =
    RecipeWordsModule.recipeWords(InstrumentationRegistry.getInstrumentation().targetContext)
