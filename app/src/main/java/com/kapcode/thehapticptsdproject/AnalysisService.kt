package com.kapcode.thehapticptsdproject

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.DocumentsContract
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*

class AnalysisService : Service() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Default + job)

    private lateinit var notificationManager: NotificationManager
    private lateinit var notificationBuilder: NotificationCompat.Builder

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
        notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Haptic Analysis")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            stopSelf()
            return START_NOT_STICKY
        }

        val fileUris = intent?.getStringArrayListExtra(EXTRA_FILE_URIS)?.map { Uri.parse(it) } ?: return START_NOT_STICKY
        val fileNames = intent.getStringArrayListExtra(EXTRA_FILE_NAMES) ?: return START_NOT_STICKY
        val parentUris = intent.getStringArrayListExtra(EXTRA_PARENT_URIS)?.map { Uri.parse(it) } ?: return START_NOT_STICKY
        
        val profileNames = intent.getStringArrayListExtra(EXTRA_PROFILES) ?: return START_NOT_STICKY
        val profiles = profileNames.map { BeatProfile.valueOf(it) }

        startForeground(NOTIFICATION_ID, createNotification("Starting analysis...", 0, 0))

        scope.launch {
            val retriever = MediaMetadataRetriever()
            
            // Calculate total initial duration for remaining time tracking
            var totalRemainingMs = 0L
            val fileDurations = LongArray(fileUris.size)
            
            for (i in fileUris.indices) {
                var duration = 0L
                try {
                    contentResolver.openFileDescriptor(fileUris[i], "r")?.use { pfd ->
                        retriever.setDataSource(pfd.fileDescriptor)
                        duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0L
                    }
                } catch (e: Exception) { }
                fileDurations[i] = duration
                totalRemainingMs += duration * profiles.size // Total work including profiles
            }

            val totalTasks = fileUris.size * profiles.size
            var tasksProcessed = 0

            for (i in fileUris.indices) {
                if (!isActive) break
                val fileUri = fileUris[i]
                val fileName = fileNames[i]
                val parentUri = parentUris[i]
                val fileDuration = fileDurations[i]
                
                for (profile in profiles) {
                    if (!isActive) break
                    val currentTaskNum = tasksProcessed + 1
                    
                    // Update detector state with current task info and total remaining audio duration
                    BeatDetector.setBatchProgress(
                        currentTaskNum, 
                        totalTasks, 
                        formatDuration(fileDuration),
                        totalRemainingMs
                    )
                    
                    val remainingText = formatDuration(totalRemainingMs)
                    val notificationText = "($currentTaskNum/$totalTasks) ~ $remainingText left. Analyzing: $fileName"
                    updateNotification(notificationText, tasksProcessed, totalTasks)
                    
                    val beats = BeatDetector.analyzeAudioUri(this@AnalysisService, fileUri, profile)
                    if (beats.isNotEmpty()) {
                        BeatDetector.saveProfile(this@AnalysisService, parentUri, fileName, profile, beats)
                    }
                    
                    tasksProcessed++
                    totalRemainingMs -= fileDuration // Subtract duration of ONE profile task
                }
            }
            
            try { retriever.release() } catch (e: Exception) { }

            if (isActive) {
                BeatDetector.setBatchProgress(0, 0, "", 0)
                val completionText = "Batch analysis complete. $tasksProcessed tasks finished."
                notificationManager.notify(NOTIFICATION_ID, 
                    notificationBuilder
                        .setContentText(completionText)
                        .setProgress(0, 0, false)
                        .setOngoing(false)
                        .build()
                )
            }
            stopForeground(STOP_FOREGROUND_DETACH)
            stopSelf()
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
