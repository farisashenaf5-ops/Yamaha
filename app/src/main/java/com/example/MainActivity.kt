package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.faris.psrs975.ui.LcdPage
import com.faris.psrs975.ui.PsrWorkstationTheme
import com.faris.psrs975.ui.WorkstationScreen

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    setContent {
      PsrWorkstationTheme {
        val vm: WorkstationViewModel = viewModel()
        val uiState by vm.uiState.collectAsStateWithLifecycle()

        val sf2Picker = rememberLauncherForActivityResult(
          contract = ActivityResultContracts.OpenDocument(),
        ) { uri ->
          if (uri != null) {
            vm.importSf2Uri(uri)
          }
        }

        val styPicker = rememberLauncherForActivityResult(
          contract = ActivityResultContracts.OpenDocument(),
        ) { uri ->
          if (uri != null) {
            vm.importStyUri(uri)
          }
        }

        val expansionPicker = rememberLauncherForActivityResult(
          contract = ActivityResultContracts.OpenDocument(),
        ) { uri ->
          if (uri != null) {
            vm.importExpansionUri(uri)
          }
        }

        val drumKitFilesPicker = rememberLauncherForActivityResult(
          contract = ActivityResultContracts.OpenMultipleDocuments(),
        ) { uris ->
          if (uris.isNotEmpty()) {
            vm.importDrumKitUris(uris)
          }
        }

        var pendingDrumSlotTarget: Pair<String, String>? = null
        val drumSlotSamplePicker = rememberLauncherForActivityResult(
          contract = ActivityResultContracts.OpenDocument(),
        ) { uri ->
          val target = pendingDrumSlotTarget
          pendingDrumSlotTarget = null
          if (uri != null && target != null) {
            vm.importDrumSlotSampleUri(target.first, target.second, uri)
          }
        }

        val micPermissionLauncher = rememberLauncherForActivityResult(
          contract = ActivityResultContracts.RequestPermission(),
        ) { granted ->
          vm.onMicrophonePermissionResult(granted)
        }

        LaunchedEffect(Unit) {
          val alreadyGranted = ContextCompat.checkSelfPermission(
            this@MainActivity,
            Manifest.permission.RECORD_AUDIO,
          ) == PackageManager.PERMISSION_GRANTED
          if (alreadyGranted) {
            vm.onMicrophonePermissionResult(true)
          }
        }

        LaunchedEffect(vm) {
          vm.events.collect { ev ->
            when (ev) {
              WorkstationUiEvent.LaunchSf2FilePicker -> {
                sf2Picker.launch(
                  arrayOf(
                    "application/octet-stream",
                    "audio/x-soundfont",
                    "application/x-soundfont",
                    "*/*",
                  ),
                )
              }
              WorkstationUiEvent.LaunchStyFilePicker -> {
                styPicker.launch(
                  arrayOf(
                    "application/octet-stream",
                    "audio/midi",
                    "audio/x-midi",
                    "*/*",
                  ),
                )
              }
              WorkstationUiEvent.LaunchExpansionFilePicker -> {
                expansionPicker.launch(
                  arrayOf(
                    "application/octet-stream",
                    "application/zip",
                    "audio/wav",
                    "audio/x-wav",
                    "audio/midi",
                    "audio/x-midi",
                    "*/*",
                  ),
                )
              }
              WorkstationUiEvent.LaunchDrumKitFilesPicker -> {
                drumKitFilesPicker.launch(
                  arrayOf(
                    "audio/*",
                    "audio/wav",
                    "audio/x-wav",
                    "audio/mpeg",
                    "audio/ogg",
                    "application/octet-stream",
                    "application/x-soundfont",
                    "*/*",
                  ),
                )
              }
              is WorkstationUiEvent.LaunchDrumSlotSamplePicker -> {
                pendingDrumSlotTarget = ev.kitId to ev.mappingId
                drumSlotSamplePicker.launch(
                  arrayOf(
                    "audio/*",
                    "audio/wav",
                    "audio/x-wav",
                    "audio/mpeg",
                    "audio/ogg",
                    "application/octet-stream",
                    "application/x-soundfont",
                    "*/*",
                  ),
                )
              }
              WorkstationUiEvent.RequestMicrophonePermission -> {
                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
              }
              is WorkstationUiEvent.ShareFile -> {
                if (ev.file.exists()) {
                  val uri = FileProvider.getUriForFile(
                    this@MainActivity,
                    "${packageName}.fileprovider",
                    ev.file,
                  )
                  val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = ev.mimeType
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                  }
                  startActivity(Intent.createChooser(shareIntent, ev.title))
                }
              }
            }
          }
        }

        BackHandler(
          enabled = uiState.showHelpOverlay || uiState.showMenuOverlay || uiState.currentLcdPage != LcdPage.MAIN,
        ) {
          when {
            uiState.showHelpOverlay -> vm.toggleHelpOverlay()
            uiState.showMenuOverlay -> vm.toggleMenuOverlay()
            uiState.currentLcdPage != LcdPage.MAIN -> vm.navigateTo(LcdPage.MAIN)
          }
        }

        WorkstationScreen(
          state = uiState,
          actions = vm,
        )
      }
    }
  }
}
