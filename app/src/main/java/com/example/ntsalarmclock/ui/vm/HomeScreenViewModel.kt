package com.example.ntsalarmclock.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.example.ntsalarmclock.alarm.AlarmScheduler

class HomeScreenViewModel(app: Application) : AndroidViewModel(app) {

    private val scheduler = AlarmScheduler(app.applicationContext)

    fun schedule(hour: Int, minute: Int) {
        scheduler.scheduleNext(hour, minute)
    }

    fun cancel() {
        scheduler.cancel()
    }
}
