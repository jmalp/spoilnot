package com.parcha.spoilnot.feature.recipes.data

import com.parcha.spoilnot.core.firebase.FirestoreChangeMapper
import com.parcha.spoilnot.feature.recipes.domain.RecipeImportService
import com.parcha.spoilnot.feature.recipes.domain.RecipeRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
interface RecipesDataModule {
    @Binds
    fun recipeRepository(impl: FirestoreRecipeRepository): RecipeRepository

    @Binds
    @IntoSet
    fun recipeChangeMapper(impl: RecipeChangeMapper): FirestoreChangeMapper
}

/** Separate so instrumented tests can replace the network importer with fixtures. */
@Module
@InstallIn(SingletonComponent::class)
interface RecipeImportModule {
    @Binds
    fun recipeImportService(impl: OkHttpRecipeImportService): RecipeImportService
}
