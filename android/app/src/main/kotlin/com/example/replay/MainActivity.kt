package com.example.replay

import android.Manifest.permission.RECORD_AUDIO
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.PermissionChecker
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity: FlutterActivity() {
    private val CHANNEL = "replay/replay.channel"
    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL).setMethodCallHandler { call, result ->
            when(call.method) {
                "startReplayForegroundService" -> {
                    val pref = getSharedPreferences("LOCALIZATION", Context.MODE_PRIVATE)
                            ?: return@setMethodCallHandler result.error("PREFERENCE_NOT_AVAILABLE", "Preference is not available.", null)
                    with (pref.edit()) {
                        putString("NOTIFICATION_TITLE", call.argument("NOTIFICATION_TITLE"))
                        putString("NOTIFICATION_TEXT", call.argument("NOTIFICATION_TEXT"))
                        commit()
                    }
                    if(!requestRecordPermission()) return@setMethodCallHandler result.success(null)
                    startForegroundService(Intent(this, ReplayForegroundService::class.java))
                }
                "stopReplayForegroundService" -> {
                    stopService(Intent(this, ReplayForegroundService::class.java))
                }
                else -> {
                    result.notImplemented()
                }
            }
        }
    }

    fun requestRecordPermission(): Boolean {
        if(checkSelfPermission(RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            return true
        }
        requestPermissions(arrayOf(RECORD_AUDIO), 144)
        return false
    }
}
