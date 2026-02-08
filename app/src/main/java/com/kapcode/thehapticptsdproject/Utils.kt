package com.kapcode.thehapticptsdproject

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract

fun applySnap(value: Float, snap: Float): Float {
    if (snap <= 0f) return value
    return (Math.round(value / snap) * snap).coerceIn(0f..1000000f)
}

fun getAudioFiles(context: Context, folderUri: Uri): List<Pair<String, Uri>> {
    val files = mutableListOf<Pair<String, Uri>>()
    try {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(folderUri, DocumentsContract.getTreeDocumentId(folderUri))
        context.contentResolver.query(childrenUri, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_MIME_TYPE), null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                if (cursor.getString(2)?.startsWith("audio/") == true) {
                    files.add(cursor.getString(0) to DocumentsContract.buildDocumentUriUsingTree(folderUri, cursor.getString(1)))
                }
            }
        }
    } catch (e: Exception) {}
    return files
}

fun getAudioFilesWithDetails(context: Context, folderUri: Uri): List<AnalysisFile> {
    val files = mutableListOf<AnalysisFile>()
    val retriever = MediaMetadataRetriever()
    try {
        val rootDocId = DocumentsContract.getTreeDocumentId(folderUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(folderUri, rootDocId)
        
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            "duration"
        )

        context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val name = cursor.getString(0)
                val id = cursor.getString(1)
                val mime = cursor.getString(2)
                if (mime?.startsWith("audio/") == true) {
                    var duration = 0L
                    
                    val durIdx = cursor.getColumnIndex("duration")
                    if (durIdx != -1) duration = cursor.getLong(durIdx)
                    
                    val fileUri = DocumentsContract.buildDocumentUriUsingTree(folderUri, id)

                    if (duration == 0L) {
                        try {
                            context.contentResolver.openFileDescriptor(fileUri, "r")?.use { pfd ->
                                retriever.setDataSource(pfd.fileDescriptor)
                                val durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                                duration = durStr?.toLong() ?: 0L
                            }
                        } catch (e: Exception) { }
                    }

                    files.add(AnalysisFile(fileUri, name, folderUri, duration))
                }
            }
        }
    } catch (e: Exception) {
        Logger.error("Error fetching audio details: ${e.message}")
    } finally {
        try { retriever.release() } catch (e: Exception) {}
    }
    return files
}
