package com.example.replay

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.*
import android.media.projection.MediaProjectionManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.Process
import io.flutter.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.io.DataOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Paths
import java.time.Instant
import kotlin.io.path.outputStream


class ReplayForegroundService : Service() {
    companion object {
        const val NOTI_CHANNEL_ID = "REPLAY_FOREGROUND_SERVICE_CHANNEL"
        const val GENERAL_NOTI_CHANNEL_ID = "GENERAL_NOTIFICATION_CHANNEL"
        const val INPUT_CHANNEL = AudioFormat.CHANNEL_IN_MONO
        const val INPUT_ENCODING = AudioFormat.ENCODING_PCM_16BIT
        var REC_BUFFER_MULTIPLIER = 30
        var isServiceRunning = false

    }

    private var isRecording = false
    private var threads = mutableMapOf<Channel, Thread>()
    private var recordedBuffers = mutableMapOf<Channel, ShortArray>()
    private val binder = ReplayServiceBinder()

    inner class ReplayServiceBinder : Binder() {
        fun getService(): ReplayForegroundService = this@ReplayForegroundService
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            throw IllegalStateException()
        }
        val pref = getSharedPreferences("SETTINGS", Context.MODE_PRIVATE)
        REC_BUFFER_MULTIPLIER = pref.getInt(IntPreference.RECORD_LENGTH.value, 30)
        createNotification()
        isRecording = true
        threads[Channel.MIC] = Thread {
            if (!pref.getBoolean(BoolPreference.USE_MIC_CHANNEL.value, false)) {
                return@Thread
            }
            try {
                val sampleRate = AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_SYSTEM)
                val bufferSize = AudioRecord.getMinBufferSize(sampleRate, INPUT_CHANNEL, INPUT_ENCODING)
                val recBufferSize = sampleRate * REC_BUFFER_MULTIPLIER
                val buffer = ShortArray(bufferSize)
                recordedBuffers[Channel.MIC] = ShortArray(recBufferSize)
                Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
                val recorder = AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, INPUT_CHANNEL, INPUT_ENCODING, bufferSize)

                var pos = 0
                recorder.startRecording()
                while (isRecording) {
                    val readBytes = recorder.read(buffer, 0, bufferSize)
                    if (readBytes > 0) {
                        if (pos + readBytes > recBufferSize) {
                            recordedBuffers[Channel.MIC] = shiftBuffer(recordedBuffers[Channel.MIC]!!, pos + readBytes - recBufferSize)
                            pos -= (pos + readBytes - recBufferSize)
                        }
                        buffer.copyInto(recordedBuffers[Channel.MIC]!!, pos, 0, readBytes)
                        pos += readBytes
                    } else {
                        throw IllegalStateException()
                    }
                }
                recorder.stop()
                recorder.release()
            } catch (e: Throwable) {
                Log.e("replay_service", "${e.message}")
            }
        }

        threads[Channel.OUTPUT] = Thread {
            if (!pref.getBoolean(BoolPreference.USE_OUTPUT_CHANNEL.value, false)) {
                return@Thread
            }
            try {
                val sampleRate = AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_SYSTEM)
                val bufferSize = AudioRecord.getMinBufferSize(sampleRate, INPUT_CHANNEL, INPUT_ENCODING)
                val recBufferSize = sampleRate * REC_BUFFER_MULTIPLIER
                val buffer = ShortArray(bufferSize)
                recordedBuffers[Channel.OUTPUT] = ShortArray(recBufferSize)
                Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
                val mediaProjectionManager = applicationContext.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                val recorder = AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, INPUT_CHANNEL, INPUT_ENCODING, bufferSize)

                var pos = 0
                recorder.startRecording()
                while (isRecording) {
                    val readBytes = recorder.read(buffer, 0, bufferSize)
                    if (readBytes > 0) {
                        if (pos + readBytes > recBufferSize) {
                            recordedBuffers[Channel.OUTPUT] = shiftBuffer(recordedBuffers[Channel.OUTPUT]!!, pos + readBytes - recBufferSize)
                            pos -= (pos + readBytes - recBufferSize)
                        }
                        buffer.copyInto(recordedBuffers[Channel.OUTPUT]!!, pos, 0, readBytes)
                        pos += readBytes
                    } else {
                        throw IllegalStateException()
                    }
                }
                recorder.stop()
                recorder.release()
            } catch (e: Throwable) {
                Log.e("replay_service", "${e.message}")
            }
        }

        for(thread in threads.values) {
            thread.start()
        }
        isServiceRunning = true
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        isRecording = false
        for(thread in threads.values) {
            thread.join()
        }
        isServiceRunning = false
        super.onDestroy()
    }

    private fun shiftBuffer(buffer: ShortArray, shift: Int): ShortArray {
        for (i in shift until buffer.size) {
            buffer[i - shift] = buffer[i]
        }
        return buffer
    }

    fun saveReplay(path: String? = null) {
        val path = path
                ?: Paths.get(getDir("flutter", MODE_PRIVATE).toString(), "./replays/").toString()
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
                GENERAL_NOTI_CHANNEL_ID,
                "General",
                NotificationManager.IMPORTANCE_HIGH
        )
        manager.createNotificationChannel(channel)
        val localizationPref = getSharedPreferences("LOCALIZATION", Context.MODE_PRIVATE)
        val managerCompat = NotificationManagerCompat.from(this)
        managerCompat.notify(12, NotificationCompat.Builder(this, GENERAL_NOTI_CHANNEL_ID)
                .setContentTitle(localizationPref.getString("SAVE_IN_PROGRESS_TITLE", "SAVE_IN_PROGRESS_TITLE"))
                .setProgress(0, 0, true)
                .setSmallIcon(R.drawable.ic_replay_white_24dp)
                .build())

        val buffers = recordedBuffers
        val sampleRate = AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_SYSTEM)
        var output: DataOutputStream
        try {
            val settingsPref = getSharedPreferences("SETTINGS", Context.MODE_PRIVATE)
            if(settingsPref.getBoolean(BoolPreference.SAVE_SEPERATELY_BY_CHANNEL.value, false)) {
                for(key in buffers.keys) {
                    val file = Files.createFile(Paths.get(path, "${Instant.now()}-${key}.wav"))
                    // https://stackoverflow.com/questions/37281430/how-to-convert-pcm-file-to-wav-or-mp3
                    output = DataOutputStream(file.outputStream())
                    val buffer = buffers[key]!!
                    // WAVE header
                    // see http://ccrma.stanford.edu/courses/422/projects/WaveFormat/
                    writeString(output, "RIFF") // chunk id
                    writeInt(output, 36 + buffer.size * 2) // chunk size
                    writeString(output, "WAVE") // format
                    writeString(output, "fmt ") // subchunk 1 id
                    writeInt(output, 16) // subchunk 1 size
                    writeShort(output, 1.toShort()) // audio format (1 = PCM)
                    writeShort(output, 1.toShort()) // number of channels
                    writeInt(output, sampleRate) // sample rate
                    writeInt(output, sampleRate * 2) // byte rate
                    writeShort(output, 2.toShort()) // block align
                    writeShort(output, 16.toShort()) // bits per sample
                    writeString(output, "data") // subchunk 2 id
                    writeInt(output, buffer.size * 2) // subchunk 2 size
                    val byteBuffer = ByteBuffer.allocate(buffer.size * 2).order(ByteOrder.LITTLE_ENDIAN)
                    for(data in buffer) byteBuffer.putShort(data)
                    output.write(byteBuffer.array())
                    output.close()
                }
            } else {
                val flattenBuffer = ShortArray(buffers.size * sampleRate * REC_BUFFER_MULTIPLIER)
                val channels = buffers.keys.toList()
                for(i in flattenBuffer.indices) {
                    flattenBuffer[i] = buffers[channels[i % channels.size]]!![i / channels.size]
                }
                val file = Files.createFile(Paths.get(path, "${Instant.now()}.wav"))
                // https://stackoverflow.com/questions/37281430/how-to-convert-pcm-file-to-wav-or-mp3
                output = DataOutputStream(file.outputStream())
                // WAVE header
                // see http://ccrma.stanford.edu/courses/422/projects/WaveFormat/
                writeString(output, "RIFF") // chunk id
                writeInt(output, 36 + flattenBuffer.size * 2) // chunk size
                writeString(output, "WAVE") // format
                writeString(output, "fmt ") // subchunk 1 id
                writeInt(output, 16) // subchunk 1 size
                writeShort(output, 1.toShort()) // audio format (1 = PCM)
                writeShort(output, 1.toShort()) // number of channels
                writeInt(output, sampleRate) // sample rate
                writeInt(output, sampleRate * 2) // byte rate
                writeShort(output, 2.toShort()) // block align
                writeShort(output, 16.toShort()) // bits per sample
                writeString(output, "data") // subchunk 2 id
                writeInt(output, flattenBuffer.size * 2) // subchunk 2 size
                val byteBuffer = ByteBuffer.allocate(flattenBuffer.size * 2).order(ByteOrder.LITTLE_ENDIAN)
                for(data in flattenBuffer) byteBuffer.putShort(data)
                output.write(byteBuffer.array())
                output.close()
            }

        } finally {
            managerCompat.notify(12, NotificationCompat.Builder(this, GENERAL_NOTI_CHANNEL_ID)
                    .setContentTitle(localizationPref.getString("SAVE_COMPLETE_TITLE", "SAVE_COMPLETE_TITLE"))
                    .setContentText(localizationPref.getString("SAVE_COMPLETE_TEXT", "SAVE_COMPLETE_TEXT"))
                    .setSmallIcon(R.drawable.ic_replay_white_24dp)
                    .setAutoCancel(true)
                    .build())
        }
    }

    @Throws(IOException::class)
    private fun writeInt(output: DataOutputStream, value: Int) {
        output.write(value shr 0)
        output.write(value shr 8)
        output.write(value shr 16)
        output.write(value shr 24)
    }

    @Throws(IOException::class)
    private fun writeShort(output: DataOutputStream, value: Short) {
        output.write(value.toInt() shr 0)
        output.write(value.toInt() shr 8)
    }

    private fun writeByte(output: DataOutputStream, value: Byte) {
        output.write(value.toInt() shr 0)
    }

    @Throws(IOException::class)
    private fun writeString(output: DataOutputStream, value: String) {
        for (ch in value) {
            output.write(ch.code)
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
        val saveIntent = Intent(this, ReplayNotificationActionReceiver::class.java)
        saveIntent.action = "SAVE_REPLAY"
        val savePendingIntent = PendingIntent.getBroadcast(this, 2, saveIntent, PendingIntent.FLAG_CANCEL_CURRENT)
        val turnOffIntent = Intent(this, ReplayNotificationActionReceiver::class.java)
        turnOffIntent.action = "STOP_REPLAY_SERVICE"
        val turnOffPendingIntent = PendingIntent.getBroadcast(this, 4, turnOffIntent, PendingIntent.FLAG_CANCEL_CURRENT)
        val pref = getSharedPreferences("LOCALIZATION", Context.MODE_PRIVATE) ?: throw IllegalStateException()

        if(Build.VERSION.SDK_INT > Build.VERSION_CODES.R) {
            startForeground(1, NotificationCompat.Builder(this, NOTI_CHANNEL_ID)
                    .setContentTitle(pref.getString("NOTIFICATION_TITLE", "NOTIFICATION_TITLE"))
                    .setContentText(pref.getString("NOTIFICATION_TEXT", "NOTIFICATION_TEXT"))
                    .addAction(R.drawable.ic_replay_white_24dp, pref.getString("SAVE_REPLAY_TEXT", "SAVE_REPLAY_TEXT"), savePendingIntent)
                    .addAction(R.drawable.ic_replay_white_24dp, pref.getString("TURN_OFF_TEXT", "TURN_OFF_TEXT"), turnOffPendingIntent)
                    .setSmallIcon(R.drawable.ic_replay_white_24dp)
                    .build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        }
        startForeground(1, NotificationCompat.Builder(this, NOTI_CHANNEL_ID)
                .setContentTitle(pref.getString("NOTIFICATION_TITLE", "NOTIFICATION_TITLE"))
                .setContentText(pref.getString("NOTIFICATION_TEXT", "NOTIFICATION_TEXT"))
                .addAction(R.drawable.ic_replay_white_24dp, pref.getString("SAVE_REPLAY_TEXT", "SAVE_REPLAY_TEXT"), savePendingIntent)
                .addAction(R.drawable.ic_replay_white_24dp, pref.getString("TURN_OFF_TEXT", "TURN_OFF_TEXT"), turnOffPendingIntent)
                .setSmallIcon(R.drawable.ic_replay_white_24dp)
                .build())
    }
}
