package com.parcha.spoilnot.feature.pantry.data

import com.parcha.spoilnot.core.firebase.FirestoreChangeMapper
import com.parcha.spoilnot.feature.pantry.domain.PantryRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
interface PantryDataModule {
    @Binds
    fun pantryRepository(impl: FirestorePantryRepository): PantryRepository

    @Binds
    @IntoSet
    fun pantryChangeMapper(impl: PantryChangeMapper): FirestoreChangeMapper
}
