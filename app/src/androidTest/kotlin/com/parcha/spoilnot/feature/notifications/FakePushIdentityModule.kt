package com.parcha.spoilnot.feature.notifications

import com.parcha.spoilnot.feature.notifications.data.PushIdentity
import com.parcha.spoilnot.feature.notifications.data.PushIdentityModule
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Singleton

/**
 * The emulators' demo project cannot register a Firebase installation or an FCM token, so instrumented tests use a
 * fixed identity. Device documents are then written and deleted on the Firestore emulator as on a real phone.
 */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [PushIdentityModule::class])
object FakePushIdentityModule {
    @Provides
    @Singleton
    fun fake(): FakePushIdentity = FakePushIdentity()

    @Provides
    fun identity(fake: FakePushIdentity): PushIdentity = fake
}

class FakePushIdentity : PushIdentity {
    val tokenDeletes = AtomicInteger()

    override suspend fun installId(): String = INSTALL_ID

    override suspend fun token(): String = TOKEN

    override suspend fun deleteToken() {
        tokenDeletes.incrementAndGet()
    }

    companion object {
        const val INSTALL_ID = "test-install"
        const val TOKEN = "test-token-for-instrumented-tests"
    }
}
