package com.kapcode.thehapticptsdproject

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel

class MediaFoldersViewModel : ViewModel() {
    val authorizedFolderUris: Set<String>
        get() = SettingsManager.authorizedFolderUris

    fun addFolder(context: Context, uri: Uri) {
        val current = SettingsManager.authorizedFolderUris
        if (uri.toString() !in current) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            SettingsManager.authorizedFolderUris = current + uri.toString()
            SettingsManager.save()
        }
    }

    fun removeFolder(uriString: String) {
        val current = SettingsManager.authorizedFolderUris
        SettingsManager.authorizedFolderUris = current - uriString
        SettingsManager.save()
    }
}
