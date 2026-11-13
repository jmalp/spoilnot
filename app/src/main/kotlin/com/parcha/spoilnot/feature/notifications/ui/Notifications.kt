package com.parcha.spoilnot.feature.notifications.ui

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.parcha.spoilnot.MainActivity
import com.parcha.spoilnot.R
import com.parcha.spoilnot.core.navigation.DeepLink
import com.parcha.spoilnot.feature.notifications.domain.NotificationAccess
import com.parcha.spoilnot.feature.notifications.domain.YoureSet
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject

/** The expiry channel and the local test and "You're set" notifications (DESIGN 5.4, 10.2). */
object Notifications {
    const val CHANNEL_EXPIRY = "expiry"
    private const val TEST_ID = 1
    private const val YOURE_SET_ID = 2

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
        val notification = NotificationCompat.Builder(context, CHANNEL_EXPIRY)
            .setSmallIcon(R.drawable.ic_stat_notification)
            .setContentTitle(context.getString(R.string.test_notification_title))
            .setContentText(context.getString(R.string.test_notification_body))
            .setContentIntent(openPantry(context))
            .setAutoCancel(true)
            .build()
        @Suppress("MissingPermission") // checked by enabled()
        NotificationManagerCompat.from(context).notify(TEST_ID, notification)
        return true
    }

    /**
     * "You're set" right after Turn on (DESIGN 5.4; owner's decision 2): the reminder time and tomorrow's items. It opens
     * like a digest, on Pantry's Needs attention.
     */
    fun sendYoureSet(context: Context, content: YoureSet): Boolean {
        if (!enabled(context)) return false
        @Suppress("MissingPermission") // checked by enabled()
        NotificationManagerCompat.from(context).notify(YOURE_SET_ID, youreSet(context, content))
        return true
    }

    /** The "You're set" notification itself, built apart so a test can read it. */
    fun youreSet(context: Context, content: YoureSet): Notification {
        val body = youreSetBody(context.resources, content)
        return NotificationCompat.Builder(context, CHANNEL_EXPIRY)
            .setSmallIcon(R.drawable.ic_stat_notification)
            .setContentTitle(context.getString(R.string.notif_youre_set_title))
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(openPantry(context))
            .setAutoCancel(true)
            .build()
    }

    /** "Spoil Not reminds you once a day, around 9:00 AM, … Tomorrow's reminder: Spinach, Chicken and 2 more." */
    fun youreSetBody(resources: Resources, content: YoureSet): String {
        val time = resources.getString(R.string.notif_youre_set_time, formatHour(content.hour))
        val tomorrow = when {
            content.tomorrow.isEmpty() -> resources.getString(R.string.notif_youre_set_nothing)

            content.more == 0 -> resources.getString(
                R.string.notif_youre_set_tomorrow,
                content.tomorrow.joinToString(", "),
            )

            else -> resources.getString(
                R.string.notif_youre_set_tomorrow,
                resources.getQuantityString(
                    R.plurals.notif_youre_set_more,
                    content.more,
                    content.tomorrow.joinToString(", "),
                    content.more,
                ),
            )
        }
        return "$time $tomorrow"
    }

    private fun openPantry(context: Context): PendingIntent {
        val open = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(DeepLink.EXTRA_ROUTE, DeepLink.PANTRY_EXPIRING)
        return PendingIntent.getActivity(
            context,
            0,
            open,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}

/** This phone's notification system, as [NotificationAccess] (DESIGN 10.2). */
class SystemNotificationAccess @Inject constructor(@param:ApplicationContext private val context: Context) :
    NotificationAccess {
    override fun allowed(): Boolean = Notifications.enabled(context)

    override fun showYoureSet(content: YoureSet): Boolean = Notifications.sendYoureSet(context, content)
}

@Module
@InstallIn(SingletonComponent::class)
interface NotificationAccessModule {
    @Binds
    fun access(impl: SystemNotificationAccess): NotificationAccess
}
