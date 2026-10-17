package com.parcha.spoilnot.feature.pantry.data

import com.parcha.spoilnot.feature.pantry.domain.PantryRepository
import com.parcha.spoilnot.feature.pantry.domain.PantryWriteErrors
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
interface PantryDataModule {
    @Binds
    fun pantryRepository(impl: FirestorePantryRepository): PantryRepository

    @Binds
    fun pantryWriteErrors(impl: FirestorePantryRepository): PantryWriteErrors
}
