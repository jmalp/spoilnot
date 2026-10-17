package com.parcha.spoilnot.feature.notifications.ui

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.parcha.spoilnot.MainActivity
import com.parcha.spoilnot.R
import com.parcha.spoilnot.core.navigation.DeepLink

/** The expiry channel and the local test notification (DESIGN 5.4, 10.2). */
object Notifications {
    const val CHANNEL_EXPIRY = "expiry"
    private const val TEST_ID = 1

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_EXPIRY,
            context.getString(R.string.channel_expiry),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply { description = context.getString(R.string.channel_expiry_description) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** Runtime permission on Android 13 and later. */
    fun permissionGranted(context: Context): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED

    /** False when the user turned off notifications or the expiry channel in system settings. */
    fun enabled(context: Context): Boolean {
        val manager = NotificationManagerCompat.from(context)
        val channel = manager.getNotificationChannel(CHANNEL_EXPIRY)
        return permissionGranted(context) && manager.areNotificationsEnabled() &&
            (channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE)
    }

    /** Verifies the permission and channel with a local notification that opens like a real digest. */
    fun sendTest(context: Context): Boolean {
        if (!enabled(context)) return false
        val open = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(DeepLink.EXTRA_ROUTE, DeepLink.PANTRY_EXPIRING)
        val pending = PendingIntent.getActivity(
            context,
            0,
            open,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_EXPIRY)
            .setSmallIcon(R.drawable.ic_stat_notification)
            .setContentTitle(context.getString(R.string.test_notification_title))
            .setContentText(context.getString(R.string.test_notification_body))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        @Suppress("MissingPermission") // checked by enabled()
        NotificationManagerCompat.from(context).notify(TEST_ID, notification)
        return true
    }
}
