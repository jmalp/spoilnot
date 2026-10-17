package com.parcha.spoilnot.feature.notifications.data

import com.parcha.spoilnot.feature.notifications.domain.DeviceRegistry
import com.parcha.spoilnot.feature.notifications.domain.NotificationPrefsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
interface NotificationsDataModule {
    @Binds
    fun deviceRegistry(impl: FirebaseDeviceRegistry): DeviceRegistry

    @Binds
    fun notificationPrefs(impl: FirestoreNotificationPrefsRepository): NotificationPrefsRepository
}
