package com.example.replay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ReplayNotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        when(intent?.action) {
            "SAVE_REPLAY" -> {
                val binder = peekService(context, Intent(context, ReplayForegroundService::class.java)) as ReplayForegroundService.ReplayServiceBinder
                binder.getService().saveReplay()
            }
            "STOP_REPLAY_SERVICE" -> {
                context?.stopService(Intent(context, ReplayForegroundService::class.java))
            }
        }
    }

}