package com.parcha.spoilnot.feature.notifications.data

import com.google.firebase.installations.FirebaseInstallations
import com.google.firebase.messaging.FirebaseMessaging
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import kotlinx.coroutines.tasks.await

/**
 * This install's push identity (DESIGN 10.2): the Firebase Installation ID that names its device document, and its
 * FCM token. Instrumented tests replace it, because emulator builds cannot register with FCM.
 */
interface PushIdentity {
    suspend fun installId(): String

    suspend fun token(): String

    /** FCM then rejects sends to the old token, and the reminder job removes any device document that holds it. */
    suspend fun deleteToken()
}

class FirebasePushIdentity @Inject constructor() : PushIdentity {
    override suspend fun installId(): String = FirebaseInstallations.getInstance().id.await()

    override suspend fun token(): String = FirebaseMessaging.getInstance().token.await()

    override suspend fun deleteToken() {
        FirebaseMessaging.getInstance().deleteToken().await()
    }
}

@Module
@InstallIn(SingletonComponent::class)
interface PushIdentityModule {
    @Binds
    fun pushIdentity(impl: FirebasePushIdentity): PushIdentity
}
