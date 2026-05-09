package com.safeglow.edge.ocr

import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.safeglow.edge.ocr.MLKitOcrProcessor.toRawTokens
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Converts a CameraX [ImageCapture] tap event to a list of raw OCR text tokens.
 *
 * Privacy (T-2-01): uses the bundled ML Kit variant (com.google.mlkit:text-recognition)
 * which ships the model inside the APK. No network calls, no Google Play Services.
 *
 * T-2-02 mitigation: [ImageProxy.close] is ALWAYS called in addOnCompleteListener,
 * regardless of success or failure, preventing camera buffer exhaustion.
 */
@Singleton
class OcrRepository @Inject constructor() {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val executor = Executors.newSingleThreadExecutor()

    /**
     * Captures a still image via [imageCapture] and returns the extracted text tokens.
     *
     * Suspension point: resumes on the ML Kit task callback thread.
     * ImageProxy is always closed in [addOnCompleteListener].
     */
    suspend fun extractRawTokens(imageCapture: ImageCapture): List<String> =
        suspendCancellableCoroutine { continuation ->
            imageCapture.takePicture(
                executor,
                object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) {
                        val mediaImage = image.image
                        if (mediaImage == null) {
                            image.close()
                            continuation.resume(emptyList())
                            return
                        }
                        val inputImage = InputImage.fromMediaImage(
                            mediaImage,
                            image.imageInfo.rotationDegrees
                        )
                        recognizer.process(inputImage)
                            .addOnSuccessListener { visionText ->
                                continuation.resume(visionText.toRawTokens())
                            }
                            .addOnFailureListener { e ->
                                continuation.resumeWithException(e)
                            }
                            .addOnCompleteListener {
                                // CRITICAL (T-2-02): always close ImageProxy here —
                                // fires regardless of success or failure.
                                image.close()
                            }
                    }

                    override fun onError(exception: ImageCaptureException) {
                        continuation.resumeWithException(exception)
                    }
                }
            )
        }
}
