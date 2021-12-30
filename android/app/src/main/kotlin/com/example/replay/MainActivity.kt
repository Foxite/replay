package com.example.replay

import android.Manifest.permission.RECORD_AUDIO
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.widget.Toast
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.security.InvalidParameterException

class MainActivity: FlutterActivity() {
    private val CHANNEL = "replay/replay.channel"
    private var replayService: ReplayForegroundService? = null
    private var isReplayServiceBound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            when(service) {
                is ReplayForegroundService.ReplayServiceBinder -> {
                    replayService = service.getService()
                    isReplayServiceBound = true
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            replayService = null
            isReplayServiceBound = false
        }
    }

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
                        putString("SAVE_REPLAY_TEXT", call.argument("SAVE_REPLAY_TEXT"))
                        putString("TURN_OFF_TEXT", call.argument("TURN_OFF_TEXT"))
                        putString("SAVE_COMPLETE_TITLE", call.argument("SAVE_COMPLETE_TITLE"))
                        putString("SAVE_COMPLETE_TEXT", call.argument("SAVE_COMPLETE_TEXT"))
                        putString("PERMISSION_REQUIRED", call.argument("PERMISSION_REQUIRED"))
                        commit()
                    }
                    if(!requestRecordPermission()) {
                        Toast.makeText(this, pref.getString("PERMISSION_REQUIRED", "PERMISSION_REQUIRED"), Toast.LENGTH_LONG).show()
                        result.success(null)
                        return@setMethodCallHandler
                    }
                    val serviceIntent = Intent(this, ReplayForegroundService::class.java)
                    startForegroundService(serviceIntent)
                    bindService(serviceIntent, connection, Context.BIND_AUTO_CREATE)
                    result.success(null)
                }
                "stopReplayForegroundService" -> {
                    stopService(Intent(this, ReplayForegroundService::class.java))
                    unbindService(connection)
                }
                "saveReplay" -> {
                    if(isReplayServiceBound && replayService != null) {
                        Thread {
                            replayService?.saveReplay(call.argument("PATH") ?: throw InvalidParameterException())
                        }.start()
                        result.success(null)
                        return@setMethodCallHandler
                    }
                    result.error("SERVICE_NOT_BIND", "Service is not bind.", null)
                }
                else -> {
                    result.notImplemented()
                }
            }
        }
    }

    private fun requestRecordPermission(): Boolean {
        if(checkSelfPermission(RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            return true
        }
        requestPermissions(arrayOf(RECORD_AUDIO), 144)
        return false
    }
}
