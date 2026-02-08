package com.kapcode.thehapticptsdproject

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.result.ActivityResult
import androidx.lifecycle.ViewModel

class MediaFoldersViewModel : ViewModel() {
    val folderUris = SettingsManager.authorizedFolderUrisState

    fun onAddFolder(launcher: ManagedActivityResultLauncher<Uri?, ActivityResult>) {
        launcher.launch(null)
    }

    fun onFolderAdded(context: Context, uri: Uri?) {
        uri?.let {
            context.contentResolver.takePersistableUriPermission(
                it,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            val new = folderUris.value.toMutableSet().apply { add(it.toString()) }
            SettingsManager.authorizedFolderUris = new
        }
    }

    fun onRemoveFolder(uri: String) {
        val new = folderUris.value.toMutableSet().apply { remove(uri) }
        SettingsManager.authorizedFolderUris = new
    }
}
