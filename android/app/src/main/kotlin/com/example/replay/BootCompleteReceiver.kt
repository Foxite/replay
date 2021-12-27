package com.example.replay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootCompleteReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if(intent?.action != "android.intent.action.BOOT_COMPLETED" || context == null) return
        context.startForegroundService(Intent(context, ReplayForegroundService::class.java))
    }
}