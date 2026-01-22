package com.example.ntsalarmclock.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.ntsalarmclock.RingingActivity

class AlarmReceiver : BroadcastReceiver() {

    // Starts RingingActivity when receiving the broadcast
    override fun onReceive(context: Context, intent: Intent?) {
        val i = Intent(context, RingingActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(i)
    }
}
