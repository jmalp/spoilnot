package com.parcha.spoilnot.feature.notifications.ui

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.parcha.spoilnot.feature.notifications.domain.DeviceSync
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Receives FCM events (DESIGN 10.2). In the background the system shows the digest itself; in the foreground
 * nothing is shown, because the Home card is already live.
 */
@AndroidEntryPoint
class SpoilNotMessagingService : FirebaseMessagingService() {
    @Inject lateinit var deviceSync: DeviceSync

    override fun onNewToken(token: String) {
        deviceSync.onNewToken()
    }

    override fun onMessageReceived(message: RemoteMessage) = Unit
}
