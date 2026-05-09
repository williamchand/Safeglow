package com.safeglow.edge.camera

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.safeglow.edge.ocr.OcrRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Holds the CameraX [ImageCapture] use case reference and orchestrates OCR capture.
 *
 * Architecture (from RESEARCH.md Pattern 1):
 * - CameraScreen calls [bindCamera] from LaunchedEffect with the LifecycleOwner and PreviewView.
 * - "Scan Label" button triggers [captureAndProcess] which delegates to [OcrRepository].
 * - Results are exposed via [uiState] StateFlow for Compose observation.
 *
 * ProcessCameraProvider binding happens here rather than in the composable so that
 * ListenableFuture handling is contained in the ViewModel (using suspendCancellableCoroutine
 * + addListener pattern — no guava dependency required in the composable).
 */
@HiltViewModel
class CameraViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val ocrRepository: OcrRepository
) : ViewModel() {

    private var imageCapture: ImageCapture? = null

    private val _uiState = MutableStateFlow<CameraUiState>(CameraUiState.Idle)
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    /**
     * Binds CameraX Preview + ImageCapture use cases to the given [lifecycleOwner].
     * Configures [previewView]'s surface provider for the live camera feed.
     *
     * Must be called from the main thread (LaunchedEffect default dispatcher is Main).
     */
    fun bindCamera(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView
    ) {
        viewModelScope.launch {
            val cameraProvider = awaitCameraProvider()

            val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }
            val capture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .build()
            imageCapture = capture

            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                capture
                // No ImageAnalysis — tap-to-capture only (RESEARCH anti-pattern note)
            )
        }
    }

    /**
     * Triggers a still image capture and passes the result through ML Kit OCR.
     * Updates [uiState] to [CameraUiState.Loading] while processing,
     * then to [CameraUiState.Success] or [CameraUiState.Error].
     */
    fun captureAndProcess() {
        val capture = imageCapture ?: run {
            _uiState.value = CameraUiState.Error("Camera not ready")
            return
        }
        viewModelScope.launch {
            _uiState.value = CameraUiState.Loading
            try {
                val tokens = ocrRepository.extractRawTokens(capture)
                _uiState.value = CameraUiState.Success(tokens)
            } catch (e: Exception) {
                _uiState.value = CameraUiState.Error(e.message ?: "OCR failed")
            }
        }
    }

    /**
     * Suspends until [ProcessCameraProvider] is ready.
     *
     * Uses [suspendCancellableCoroutine] + [ProcessCameraProvider.getInstance] addListener
     * to avoid requiring guava or concurrent-futures-ktx on the composable classpath.
     */
    private suspend fun awaitCameraProvider(): ProcessCameraProvider =
        suspendCancellableCoroutine { cont ->
            val future = ProcessCameraProvider.getInstance(appContext)
            val executor = ContextCompat.getMainExecutor(appContext)
            future.addListener(
                {
                    try {
                        cont.resume(future.get())
                    } catch (e: Exception) {
                        cont.resumeWithException(e)
                    }
                },
                executor
            )
        }
}

sealed interface CameraUiState {
    data object Idle : CameraUiState
    data object Loading : CameraUiState
    data class Success(val tokens: List<String>) : CameraUiState
    data class Error(val message: String) : CameraUiState
}
