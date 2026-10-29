package com.parcha.spoilnot.feature.plan.data

import com.parcha.spoilnot.core.firebase.FirestoreChangeMapper
import com.parcha.spoilnot.feature.plan.domain.MealPlanRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
interface MealPlanDataModule {
    @Binds
    fun mealPlanRepository(impl: FirestoreMealPlanRepository): MealPlanRepository

    @Binds
    @IntoSet
    fun mealPlanChangeMapper(impl: MealPlanChangeMapper): FirestoreChangeMapper
}
