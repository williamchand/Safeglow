package com.safeglow.edge.data.inference

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.withContext

/**
 * On-device Gemma 4 E2B inference service.
 *
 * Threading rule (NON-NEGOTIABLE, RESEARCH.md lines 374–375, 407–410):
 * Every Engine call MUST run on `inferenceDispatcher`. The GPU delegate is bound to the
 * thread that called initialize(); the shared IO dispatcher has no thread
 * affinity guarantee — using it produces "TfLiteGpuDelegate Invoke: GpuDelegate must
 * run on the same thread where it was initialized" crashes under load.
 *
 * Model path rule (T-1-03):
 * Path is constructed only from context.filesDir.resolve(literal-name). No public
 * setter, no constructor parameter accepts a path. Closes path-traversal surface.
 *
 * GPU silent-failure rule (RESEARCH.md line 185, GitHub issue #1860):
 * Backend.GPU() can construct and engine.initialize() can return without exception
 * on Tensor G3 (Pixel 8 Pro), but inference crashes with "Can not find OpenCL
 * library". The try/catch around `Engine(GPU).also { it.initialize() }` is the only
 * correct mitigation — no pre-check API exists for the LiteRT-LM Engine.
 */
@Singleton
class LiteRTInferenceService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val inferenceDispatcher =
        Executors.newSingleThreadExecutor().asCoroutineDispatcher()

    @Volatile
    private var engine: Engine? = null

    suspend fun initialize(): Unit = withContext(inferenceDispatcher) {
        if (engine != null) return@withContext

        val modelPath = context.filesDir.resolve(MODEL_FILENAME).absolutePath
        val cacheDir = context.cacheDir.absolutePath

        engine = try {
            Engine(
                EngineConfig(
                    modelPath = modelPath,
                    backend = Backend.GPU(),
                    cacheDir = cacheDir
                )
            ).also { it.initialize() }
        } catch (e: Exception) {
            Log.w(TAG, "GPU init failed (${e.message}), falling back to CPU")
            Engine(
                EngineConfig(
                    modelPath = modelPath,
                    backend = Backend.CPU(),
                    cacheDir = cacheDir
                )
            ).also { it.initialize() }
        }
    }

    fun infer(prompt: String): Flow<String> = channelFlow {
        withContext(inferenceDispatcher) {
            val active = checkNotNull(engine) {
                "Engine not initialized — call initialize() first"
            }
            active.createConversation().use { conversation ->
                conversation.sendMessageAsync(prompt).collect { message ->
                    send(message.toString())
                }
            }
        }
        awaitClose { /* engine lifecycle is owned by close(); nothing to release here */ }
    }

    fun close() {
        engine?.close()
        engine = null
        inferenceDispatcher.close()
    }

    companion object {
        private const val TAG = "LiteRTInferenceService"
        private const val MODEL_FILENAME = "gemma-4-E2B-it.litertlm"
    }
}
