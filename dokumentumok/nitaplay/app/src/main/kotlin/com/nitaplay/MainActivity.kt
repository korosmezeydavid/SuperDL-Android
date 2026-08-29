package com.nitaplay

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.nitaplay.ui.NitaPlayAppRoot
import com.nitaplay.ui.NitaPlayViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: NitaPlayViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        if (granted.values.any { it }) {
            viewModel.loadCurrentSource()
        }
    }

    private val folderPicker = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) viewModel.onCloudFolderPicked(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        requestNeededPermissions()

        setContent {
            val ui by viewModel.ui.collectAsState()
            val playback by viewModel.playback.collectAsState()
            NitaPlayAppRoot(
                ui = ui,
                playback = playback,
                vm = viewModel,
                onPickCloudFolder = { folderPicker.launch(null) }
            )
        }
    }

    private fun requestNeededPermissions() {
        val needed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!has(Manifest.permission.READ_MEDIA_AUDIO)) {
                needed += Manifest.permission.READ_MEDIA_AUDIO
            }
            if (!has(Manifest.permission.POST_NOTIFICATIONS)) {
                needed += Manifest.permission.POST_NOTIFICATIONS
            }
        } else {
            if (!has(Manifest.permission.READ_EXTERNAL_STORAGE)) {
                needed += Manifest.permission.READ_EXTERNAL_STORAGE
            }
        }
        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        } else {
            viewModel.loadCurrentSource()
        }
    }

    private fun has(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
}
