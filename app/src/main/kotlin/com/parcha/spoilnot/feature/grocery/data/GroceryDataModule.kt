package com.parcha.spoilnot.feature.grocery.data

import com.parcha.spoilnot.core.firebase.FirestoreChangeMapper
import com.parcha.spoilnot.feature.grocery.domain.GroceryRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
interface GroceryDataModule {
    @Binds
    fun groceryRepository(impl: FirestoreGroceryRepository): GroceryRepository

    @Binds
    @IntoSet
    fun groceryChangeMapper(impl: GroceryChangeMapper): FirestoreChangeMapper
}
