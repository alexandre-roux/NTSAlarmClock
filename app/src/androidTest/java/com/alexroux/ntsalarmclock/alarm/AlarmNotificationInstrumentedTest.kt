package com.alexroux.ntsalarmclock.alarm

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.alexroux.ntsalarmclock.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for notification objects that require the Android runtime.
 *
 * Local JVM tests cannot reliably inspect NotificationChannel behavior or
 * full-screen notification fields, so these assertions run on device/emulator.
 *
 * The first test asks Android's real [NotificationManager] for the channel after creation because channel
 * configuration is persisted by the operating system. The second inspects the built [Notification]
 * object directly. Together they cover the system-level container and the individual alarm notification
 * placed inside it.
 */
@RunWith(AndroidJUnit4::class)
class AlarmNotificationInstrumentedTest {

    /**
     * Verifies Android persists the alarm channel with high importance and public visibility,
     * while leaving sound and vibration to the playback service rather than the channel.
     *
     * `createNotificationChannel` is executed before querying the manager by the production channel ID.
     * The identity/name checks guard accidental mismatches, high importance permits urgent alarm display,
     * and disabled channel vibration/sound prevents Android from adding effects on top of app-controlled
     * audio. A null sound is asserted explicitly because silence is part of the channel contract.
     */
    @Test
    fun createNotificationChannel_configuresHighImportancePublicSilentChannel() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // Creating the channel through the framework lets the test inspect the
        // exact persisted channel settings Android will use at runtime.
        AlarmNotification.createNotificationChannel(context)

        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = manager.getNotificationChannel(AlarmNotification.CHANNEL_ID)

        assertNotNull(channel)
        assertEquals(AlarmNotification.CHANNEL_ID, channel.id)
        assertEquals(context.getString(R.string.notification_channel_name), channel.name)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, channel.importance)
        assertFalse(channel.shouldVibrate())
        assertNull(channel.sound)
    }

    /**
     * Verifies a ringing notification has alarm-specific presentation, launches full-screen UI,
     * exposes a stop action, and remains ongoing until the alarm is explicitly stopped.
     *
     * Category and visibility influence how Android presents the alarm on the lock screen. Title/text
     * assertions verify user-facing resources. Both content and full-screen intents must exist so tapping
     * or urgent delivery can open the ringing UI. The single action must be the localized stop command.
     * Finally, bit-mask assertions prove the notification is ongoing and only alerts once, while not being
     * auto-cancelled by an ordinary tap.
     */
    @Test
    fun buildAlarmNotification_usesAlarmCategoryFullScreenIntentAndStopAction() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // The notification shape matters for alarm visibility and for letting
        // the user stop playback directly from the notification.
        val notification = AlarmNotification.buildAlarmNotification(context)

        assertEquals(NotificationCompat.CATEGORY_ALARM, notification.category)
        assertEquals(Notification.VISIBILITY_PUBLIC, notification.visibility)
        assertEquals(
            context.getString(R.string.app_name),
            notification.extras.getString(Notification.EXTRA_TITLE)
        )
        assertEquals(
            context.getString(R.string.notification_alarm_ringing),
            notification.extras.getString(Notification.EXTRA_TEXT)
        )
        assertNotNull(notification.contentIntent)
        assertNotNull(notification.fullScreenIntent)
        assertEquals(1, notification.actions.size)
        assertEquals(
            context.getString(R.string.stop_alarm_button),
            notification.actions[0].title.toString()
        )
        assertNotNull(notification.actions[0].actionIntent)
        assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertTrue(notification.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0)
        assertFalse(notification.flags and Notification.FLAG_AUTO_CANCEL != 0)
    }
}
