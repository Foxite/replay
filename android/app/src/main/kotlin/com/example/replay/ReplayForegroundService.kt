package com.example.replay

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.*
import android.os.IBinder
import android.os.Process
import io.flutter.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import java.time.Instant


class ReplayForegroundService : Service() {
    companion object {
        const val NOTI_CHANNEL_ID = "REPLAY_FOREGROUND_SERVICE_CHANNEL"
        const val INPUT_CHANNEL = AudioFormat.CHANNEL_IN_MONO
        const val INPUT_ENCODING = AudioFormat.ENCODING_PCM_8BIT
        const val REC_BUFFER_MULTIPLIER = 30
    }
    private var isRecording = false
    private var thread: Thread? = null
    override fun onBind(intent: Intent?): IBinder? {
        TODO("Not yet implemented")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            throw IllegalStateException()
        }
        createNotification()
        thread = Thread {
            try {
                val sampleRate = AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_SYSTEM)
                val bufferSize = AudioRecord.getMinBufferSize(sampleRate, INPUT_CHANNEL, INPUT_ENCODING)
                val recBufferSize = sampleRate * REC_BUFFER_MULTIPLIER
                val buffer = ByteArray(bufferSize)
                Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)

                val micRecorder = AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, INPUT_CHANNEL, INPUT_ENCODING, bufferSize)
                val recordedBuffer = ByteArray(recBufferSize)
                var pos = 0
                isRecording = true
                micRecorder.startRecording()
                val startTime = Instant.now()
                while(isRecording) {
                    val readBytes = micRecorder.read(buffer, 0, bufferSize)
                    if(readBytes > 0) {
                        if(pos + readBytes > recBufferSize) {
                            shiftBuffer(buffer, pos + readBytes - recBufferSize)
                            pos -= (pos + readBytes - recBufferSize)
                        }
                        buffer.copyInto(recordedBuffer, pos, readBytes)
                        pos += readBytes
                    }
                }
                micRecorder.release()
            } catch(e: Throwable) {
                throw e
            }
        }
        thread?.start()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        isRecording = false
        super.onDestroy()
    }

    private fun shiftBuffer(buffer: ByteArray, shift: Int) {
        for(i in shift until buffer.size) {
            buffer[i - shift] = buffer[i]
        }
    }

    private fun createNotification() {
        val channel = NotificationChannel(
                NOTI_CHANNEL_ID,
                "Replay Foreground Service",
                NotificationManager.IMPORTANCE_DEFAULT
        )
        val manager = getSystemService(NotificationManager::class.java) as NotificationManager
        manager.createNotificationChannel(channel)
        val notiIntent = Intent(this, MainActivity::class.java)
        notiIntent.action = "SAVE_REPLAY"
        val pendingIntent = PendingIntent.getActivity(this, 0, notiIntent, PendingIntent.FLAG_CANCEL_CURRENT + PendingIntent.FLAG_IMMUTABLE)
        val pref = getSharedPreferences("LOCALIZATION", Context.MODE_PRIVATE) ?: throw IllegalStateException()
        startForeground(1, NotificationCompat.Builder(this, NOTI_CHANNEL_ID)
                .setContentTitle(pref.getString("NOTIFICATION_TITLE", ""))
                .setContentText(pref.getString("NOTIFICATION_TEXT", ""))
                .setContentIntent(pendingIntent)
                .setSmallIcon(R.drawable.ic_replay_white_24dp)
                .build())
    }
}
