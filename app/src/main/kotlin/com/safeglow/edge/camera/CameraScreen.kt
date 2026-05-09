package com.safeglow.edge.camera

import android.Manifest
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale

/**
 * Camera capture screen for Phase 2 label scanning (SCAN-01).
 *
 * Architecture (RESEARCH.md Pattern 1):
 * - Accompanist [rememberPermissionState] handles CAMERA permission state machine.
 * - [AndroidView] wraps [PreviewView] — camera-compose is alpha, not used here.
 * - [LaunchedEffect] calls [CameraViewModel.bindCamera] which internally suspends until
 *   [ProcessCameraProvider] is ready (no guava types exposed to the composable).
 * - "Scan Label" button calls [CameraViewModel.captureAndProcess].
 *
 * Privacy (T-2-01): no INTERNET permission; ML Kit bundled model runs offline.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraScreen(
    viewModel: CameraViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val permissionState = rememberPermissionState(Manifest.permission.CAMERA)

    if (!permissionState.status.isGranted) {
        // Permission not yet granted — show rationale or request button
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val message = if (permissionState.status.shouldShowRationale) {
                    "Camera access is required to scan product labels."
                } else {
                    "Grant camera permission to scan labels."
                }
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )
                Button(onClick = { permissionState.launchPermissionRequest() }) {
                    Text("Grant Camera Permission")
                }
            }
        }
        return
    }

    val previewView = remember { PreviewView(context) }

    // Bind CameraX use cases via ViewModel (Pitfall 3: binding must happen on main thread;
    // viewModelScope.launch defaults to Main, so bindCamera is safe here).
    LaunchedEffect(Unit) {
        viewModel.bindCamera(lifecycleOwner, previewView)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )

        // Scan button — bottom center overlay
        Button(
            onClick = { viewModel.captureAndProcess() },
            enabled = uiState !is CameraUiState.Loading,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(32.dp)
        ) {
            if (uiState is CameraUiState.Loading) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(end = 8.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
            Text("Scan Label")
        }

        // Status overlay (tokens or error)
        when (val state = uiState) {
            is CameraUiState.Success -> {
                Text(
                    text = "Found ${state.tokens.size} ingredient(s)",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                )
            }
            is CameraUiState.Error -> {
                Text(
                    text = "Error: ${state.message}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                )
            }
            else -> Unit
        }
    }
}
