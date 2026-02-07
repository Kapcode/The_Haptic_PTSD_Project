package com.kapcode.thehapticptsdproject

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
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
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Replace with your app's icon
            .setOngoing(true)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            stopSelf()
            return START_NOT_STICKY
        }

        val folderUris = intent?.getStringArrayListExtra(EXTRA_FOLDER_URIS)?.map { Uri.parse(it) } ?: return START_NOT_STICKY
        val profileNames = intent.getStringArrayListExtra(EXTRA_PROFILES) ?: return START_NOT_STICKY
        val profiles = profileNames.map { BeatProfile.valueOf(it) }

        startForeground(NOTIFICATION_ID, createNotification("Starting analysis...", 0, 0))

        scope.launch {
            val totalFiles = folderUris.sumOf { getFileCount(it) } * profiles.size
            var filesProcessed = 0

            for (folderUri in folderUris) {
                val files = getFilesFromFolder(folderUri)
                for ((fileName, fileUri) in files) {
                    if (!isActive) break
                    for (profile in profiles) {
                        if (!isActive) break
                        val notificationText = "Analyzing: $fileName (${profile.name})"
                        updateNotification(notificationText, filesProcessed, totalFiles)
                        
                        val beats = BeatDetector.analyzeAudioUri(this@AnalysisService, fileUri, profile)
                        if (beats.isNotEmpty()) {
                            BeatDetector.saveProfile(this@AnalysisService, folderUri, fileName, profile, beats)
                        }
                        filesProcessed++
                    }
                }
            }
            
            // Analysis complete or cancelled
            if (isActive) {
                val completionText = "Batch analysis complete. $filesProcessed profiles generated."
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

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }

    private fun getFileCount(folderUri: Uri): Int {
        var count = 0
        try {
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(folderUri, DocumentsContract.getTreeDocumentId(folderUri))
            contentResolver.query(childrenUri, arrayOf(DocumentsContract.Document.COLUMN_MIME_TYPE), null, null, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    if (cursor.getString(0)?.startsWith("audio/") == true) {
                        count++
                    }
                }
            }
        } catch (e: Exception) {
            Logger.error("Error counting files: ${e.message}")
        }
        return count
    }

    private fun getFilesFromFolder(folderUri: Uri): List<Pair<String, Uri>> {
        val files = mutableListOf<Pair<String, Uri>>()
        try {
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(folderUri, DocumentsContract.getTreeDocumentId(folderUri))
            contentResolver.query(childrenUri, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_MIME_TYPE), null, null, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    val name = cursor.getString(0)
                    val id = cursor.getString(1)
                    val mime = cursor.getString(2)
                    if (mime.startsWith("audio/")) {
                        files.add(name to DocumentsContract.buildDocumentUriUsingTree(folderUri, id))
                    }
                }
            }
        } catch (e: Exception) {
            Logger.error("Error getting files: ${e.message}")
        }
        return files
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Analysis Service",
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val NOTIFICATION_ID = 2
        private const val CHANNEL_ID = "AnalysisServiceChannel"
        const val EXTRA_FOLDER_URIS = "folder_uris"
        const val EXTRA_PROFILES = "profiles"
        const val ACTION_CANCEL = "ACTION_CANCEL"
    }
}
