package com.parcha.spoilnot.core.firebase

import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.parcha.spoilnot.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Provides the Firebase SDK entry points. Only repositories in feature data/ packages may inject these
 * (DESIGN 7.2); UI and domain code depend on repository interfaces instead.
 */
@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {
    @Provides
    @Singleton
    fun firebaseAuth(): FirebaseAuth =
        FirebaseAuth.getInstance().also { FirebaseEmulators.configureAuth(it, BuildConfig.FIREBASE_EMULATOR_HOST) }

    @Provides
    @Singleton
    fun firestoreProvider(): FirestoreProvider =
        FirestoreProvider(FirebaseApp.getInstance(), BuildConfig.FIREBASE_EMULATOR_HOST)
}
