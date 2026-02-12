@file:Suppress("SameParameterValue", "SameParameterValue", "SameParameterValue")

package com.kapcode.thehapticptsdproject

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

class AnalysisService : Service() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Default + job)

    private lateinit var notificationManager: NotificationManager
    private lateinit var notificationBuilder: NotificationCompat.Builder

    private val taskChannel = Channel<AnalysisTask>(Channel.UNLIMITED)
    private var isProcessing = false

    data class AnalysisTask(
        val fileUri: Uri,
        val fileName: String,
        val parentUri: Uri,
        val profile: BeatProfile
    )

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
        notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Haptic Analysis")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)

        startTaskProcessor()
    }

    private fun startTaskProcessor() {
        scope.launch {
            for (task in taskChannel) {
                isProcessing = true
                processTask(task)
                isProcessing = false
                
                // If channel is empty after a task, we could potentially stopSelf() 
                // but we rely on START_STICKY and manual stop for now.
                // Actually, let's check if we should stop.
                if (taskChannel.isEmpty) {
                    stopForeground(STOP_FOREGROUND_DETACH)
                    // We don't stopSelf immediately to allow more tasks to come in 
                    // without recreating the service if they are close together.
                }
            }
        }
    }

    private suspend fun processTask(task: AnalysisTask) {
        val retriever = MediaMetadataRetriever()
        var duration = 0L
        try {
            contentResolver.openFileDescriptor(task.fileUri, "r")?.use { pfd ->
                retriever.setDataSource(pfd.fileDescriptor)
                duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0L
            }
        } catch (e: Exception) { }
        finally {
            try { retriever.release() } catch (e: Exception) {}
        }

        BeatDetector.setBatchProgress(1, 1, formatDuration(duration), duration)
        updateNotification("Analyzing: ${task.fileName} (${task.profile.name})", 0, 1)

        val beats = BeatDetector.analyzeAudioUri(this@AnalysisService, task.fileUri, task.profile)
        if (beats.isNotEmpty()) {
            BeatDetector.saveProfile(this@AnalysisService, task.parentUri, task.fileName, task.profile, beats)
        }
        
        BeatDetector.setBatchProgress(0, 0, "", 0)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            stopSelf()
            return START_NOT_STICKY
        }

        val fileUris = intent?.getStringArrayListExtra(EXTRA_FILE_URIS)?.map { it.toUri() }
        val fileNames = intent?.getStringArrayListExtra(EXTRA_FILE_NAMES)
        val parentUris = intent?.getStringArrayListExtra(EXTRA_PARENT_URIS)?.map { it.toUri() }
        val profileNames = intent?.getStringArrayListExtra(EXTRA_PROFILES)

        if (fileUris != null && fileNames != null && parentUris != null && profileNames != null) {
            startForeground(NOTIFICATION_ID, createNotification("Queuing analysis tasks...", 0, 0))
            
            scope.launch {
                for (i in fileUris.indices) {
                    for (pName in profileNames) {
                        taskChannel.send(AnalysisTask(
                            fileUris[i],
                            fileNames[i],
                            parentUris[i],
                            BeatProfile.valueOf(pName)
                        ))
                    }
                }
            }
        }

        return START_STICKY
    }

    private fun formatDuration(ms: Long): String {
        val totalSecs = ms / 1000
        val mins = totalSecs / 60
        val secs = totalSecs % 60
        return if (mins > 0) "${mins}m ${secs}s" else "${secs}s"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        taskChannel.close()
        job.cancel()
    }

    private fun createNotification(text: String, progress: Int, max: Int): Notification {
        return notificationBuilder
            .setContentText(text)
            .setProgress(max, progress, false)
            .build()
    }

    private fun updateNotification(text: String, progress: Int, max: Int) {
        notificationManager.notify(NOTIFICATION_ID, createNotification(text, progress, max))
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Analysis Service",
            NotificationManager.IMPORTANCE_LOW
        )
        notificationManager.createNotificationChannel(channel)
    }

    companion object {
        private const val NOTIFICATION_ID = 2
        private const val CHANNEL_ID = "AnalysisServiceChannel"
        const val EXTRA_FILE_URIS = "file_uris"
        const val EXTRA_FILE_NAMES = "file_names"
        const val EXTRA_PARENT_URIS = "parent_uris"
        const val EXTRA_PROFILES = "profiles"
        const val ACTION_CANCEL = "ACTION_CANCEL"
    }
}
