package com.kapcode.thehapticptsdproject

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kapcode.thehapticptsdproject.composables.SectionCard

@Composable
fun MediaFoldersCard(vm: MediaFoldersViewModel = viewModel()) {
    val context = LocalContext.current
    val uris by vm.folderUris
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        vm.onFolderAdded(context, uri)
    }
    SectionCard(
        title = "Media Folders",
        actions = { IconButton(onClick = { launcher.launch(null) }) { Icon(Icons.Default.Add, null) } }) {
        uris.forEach { u ->
            Row(
                modifier = Modifier.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    Uri.parse(u).path?.substringAfterLast(':') ?: "Folder",
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp)
                )
                IconButton(onClick = { vm.onRemoveFolder(u) }) {
                    Icon(
                        Icons.Default.Delete,
                        null,
                        tint = Color.Gray
                    )
                }
            }
        }
    }
}
