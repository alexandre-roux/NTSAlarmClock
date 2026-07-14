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
 */
@RunWith(AndroidJUnit4::class)
class AlarmNotificationInstrumentedTest {

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
        assertEquals(AlarmNotification.CHANNEL_NAME, channel.name)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, channel.importance)
        assertFalse(channel.shouldVibrate())
        assertNull(channel.sound)
    }

    @Test
    fun buildAlarmNotification_usesAlarmCategoryFullScreenIntentAndStopAction() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // The notification shape matters for alarm visibility and for letting
        // the user stop playback directly from the notification.
        val notification = AlarmNotification.buildAlarmNotification(context).build()

        assertEquals(NotificationCompat.CATEGORY_ALARM, notification.category)
        assertEquals(Notification.VISIBILITY_PUBLIC, notification.visibility)
        assertEquals(
            context.getString(R.string.app_name),
            notification.extras.getString(Notification.EXTRA_TITLE)
        )
        assertEquals("Alarm ringing", notification.extras.getString(Notification.EXTRA_TEXT))
        assertNotNull(notification.contentIntent)
        assertNotNull(notification.fullScreenIntent)
        assertEquals(1, notification.actions.size)
        assertEquals("Stop", notification.actions[0].title.toString())
        assertNotNull(notification.actions[0].actionIntent)
        assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertTrue(notification.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0)
        assertFalse(notification.flags and Notification.FLAG_AUTO_CANCEL != 0)
    }
}
