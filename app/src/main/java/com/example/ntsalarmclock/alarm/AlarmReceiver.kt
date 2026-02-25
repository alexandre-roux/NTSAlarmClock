package com.example.ntsalarmclock.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.ntsalarmclock.RingingActivity
import com.example.ntsalarmclock.data.DataStoreAlarmSettingsRepository
import com.example.ntsalarmclock.data.alarmSettingsDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val i = Intent(context, RingingActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(i)

        val pendingResult = goAsync()

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            try {
                val repository = DataStoreAlarmSettingsRepository(context.alarmSettingsDataStore)
                val settings = repository.settings.first()
                AlarmScheduler(context).scheduleNextFromSettings(settings)
            } catch (_: Exception) {
            } finally {
                pendingResult.finish()
            }
        }
    }
}