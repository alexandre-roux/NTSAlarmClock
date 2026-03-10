package com.example.ntsalarmclock.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.ntsalarmclock.NTSAlarmClockApplication
import com.example.ntsalarmclock.playback.PlaybackService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * BroadcastReceiver triggered by AlarmManager when the alarm fires.
 *
 * Responsibilities:
 * - Acquire a temporary wake lock to keep the CPU awake
 * - Start the playback foreground service
 * - Reschedule the next alarm only for recurring alarms
 * - Disable one shot alarms after they have fired
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {

        // Acquire a short wake lock to prevent the CPU from sleeping
        // while the receiver is still doing work.
        val wakeLock = acquireShortWakeLock(context)

        // goAsync() allows the receiver to continue work asynchronously
        val pendingResult = goAsync()

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        val app = context.applicationContext as NTSAlarmClockApplication

        try {

            // Start the foreground playback service responsible for the alarm audio
            startPlaybackService(context)

        } catch (t: Throwable) {
            Log.e(TAG, "AlarmReceiver failed", t)
        }

        scope.launch {
            try {

                val settings = app.repository.settings.first()

                if (settings.enabledDays.isEmpty()) {

                    Log.d(TAG, "One shot alarm fired, disabling it")

                    app.repository.setEnabled(false)
                    app.alarmScheduler.cancelAlarm()

                } else {

                    Log.d(TAG, "Recurring alarm fired, scheduling next occurrence")

                    app.alarmScheduler.scheduleNextAlarm(
                        hour = settings.hour,
                        minute = settings.minute,
                        enabledDays = settings.enabledDays
                    )
                }

            } catch (t: Throwable) {

                Log.e(TAG, "Failed to handle post alarm scheduling", t)

            } finally {

                wakeLock?.let {
                    if (it.isHeld) it.release()
                }

                pendingResult.finish()
            }
        }
    }

    /**
     * Starts the foreground playback service responsible for playing the alarm audio.
     */
    private fun startPlaybackService(context: Context) {

        val intent = Intent(context, PlaybackService::class.java).apply {
            action = PlaybackService.ACTION_START_ALARM
        }

        try {
            ContextCompat.startForegroundService(context, intent)
            Log.d(TAG, "PlaybackService started")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to start PlaybackService", t)
        }
    }

    /**
     * Acquires a short partial wake lock to keep the CPU awake
     * while the receiver completes its work.
     */
    private fun acquireShortWakeLock(context: Context): PowerManager.WakeLock? {

        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager

        val wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "NTSAlarmClock:AlarmReceiver"
        )

        return try {
            wakeLock.acquire(10_000L)
            wakeLock
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        private const val TAG = "AlarmReceiver"
    }
}