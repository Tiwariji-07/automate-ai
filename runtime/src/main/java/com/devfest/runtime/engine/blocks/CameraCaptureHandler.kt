package com.devfest.runtime.engine.blocks

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.devfest.runtime.engine.FlowBlockHandler
import com.devfest.runtime.engine.FlowExecutionInput
import com.devfest.runtime.engine.FlowExecutionState
import com.devfest.runtime.engine.FlowStepResult
import com.devfest.runtime.engine.FlowStepStatus
import com.devfest.runtime.model.FlowBlock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class CameraCaptureHandler(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner // Needed for binding camera lifecycle
) : FlowBlockHandler {

    override suspend fun handle(
        block: FlowBlock,
        input: FlowExecutionInput,
        state: FlowExecutionState
    ): FlowStepResult {
        return try {
            val photoFile = takePhoto(block.params)
            state.variables["last_photo"] = photoFile.absolutePath
            FlowStepResult(block.id, FlowStepStatus.SUCCESS, "Photo saved: ${photoFile.name}")
        } catch (e: Exception) {
            FlowStepResult(block.id, FlowStepStatus.FAILED, "Photo failed: ${e.message}")
        }
    }

    private suspend fun takePhoto(params: Map<String, String>): File = withContext(Dispatchers.Main) {
        val cameraProvider = getCameraProvider(context)
        val imageCapture = ImageCapture.Builder().build()
        
        val lensFacing = when (params["lens"]?.lowercase()) {
            "back", "rear" -> CameraSelector.DEFAULT_BACK_CAMERA
            else -> CameraSelector.DEFAULT_FRONT_CAMERA // Default to front for "selfie" / "intruder"
        }

        // Unbind use cases before rebinding
        cameraProvider.unbindAll()

        // Bind use cases to camera
        cameraProvider.bindToLifecycle(
            lifecycleOwner,
            lensFacing,
            imageCapture
        )

        // Create output file
        val outputDir = context.getExternalFilesDir(null) ?: context.filesDir
        val photoFile = File(
            outputDir,
            SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US)
                .format(System.currentTimeMillis()) + ".jpg"
        )

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        suspendCoroutine { cont ->
            imageCapture.takePicture(
                outputOptions,
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onError(exc: ImageCaptureException) {
                        cont.resumeWithException(exc)
                    }

                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                        cont.resume(photoFile)
                    }
                }
            )
        }
    }

    private suspend fun getCameraProvider(context: Context): ProcessCameraProvider =
        suspendCoroutine { cont ->
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProviderFuture.addListener({
                try {
                    cont.resume(cameraProviderFuture.get())
                } catch (e: Exception) {
                    cont.resumeWithException(e)
                }
            }, ContextCompat.getMainExecutor(context))
        }
}
