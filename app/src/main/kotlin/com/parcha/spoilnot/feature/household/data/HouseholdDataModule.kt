package com.parcha.spoilnot.feature.household.data

import com.parcha.spoilnot.feature.household.domain.HouseholdRepository
import com.parcha.spoilnot.feature.household.domain.LocalData
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface HouseholdDataModule {
    @Binds
    @Singleton
    fun householdRepository(impl: FirestoreHouseholdRepository): HouseholdRepository

    @Binds
    @Singleton
    fun localData(impl: FirestoreLocalData): LocalData
}
