package com.parcha.spoilnot.feature.auth.data

import com.parcha.spoilnot.feature.auth.domain.AuthRepository
import com.parcha.spoilnot.feature.auth.domain.UserRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface AuthDataModule {
    @Binds
    @Singleton
    fun authRepository(impl: FirebaseAuthRepository): AuthRepository

    @Binds
    @Singleton
    fun userRepository(impl: FirestoreUserRepository): UserRepository
}
