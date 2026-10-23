package com.devfest.automation.platform

import android.annotation.SuppressLint
import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.devfest.core.catalog.Requirement
import com.devfest.core.engine.StepHandler
import com.devfest.core.engine.StepOutcome
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Camera and location. */
class SensorHandlers(private val context: Context) {

    fun create(): Map<String, StepHandler> = mapOf(
        "take_photo" to StepHandler { _, params, _ ->
            needs(Requirement.CAMERA)?.let { return@StepHandler it }
            val file = camera.withLock { capture(params["camera"] == "back") }
            StepOutcome.Done("Saved ${file.name}", mapOf("photo.path" to file.absolutePath))
        },
        "get_location" to StepHandler { _, params, _ ->
            needs(Requirement.LOCATION)?.let { return@StepHandler it }
            location(params["accuracy"] == "high")
        }
    )

    private fun needs(requirement: Requirement): StepOutcome.Failed? =
        if (Requirements.isGranted(context, requirement)) null
        else StepOutcome.Failed("Needs \"${Requirements.label(requirement)}\"")

    private suspend fun capture(back: Boolean): File = withContext(Dispatchers.Main) {
        val provider = ProcessCameraProvider.getInstance(context).await()
        val owner = OneShotLifecycle()
        val capture = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
        val selector = if (back) CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA
        val file = File(photoDir(context), "IMG_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.jpg")
        try {
            provider.bindToLifecycle(owner, selector, capture)
            // Auto-exposure needs a moment, otherwise the first frame is dark.
            delay(WARM_UP_MS)
            try {
                takePicture(capture, file)
            } catch (e: ImageCaptureException) {
                delay(WARM_UP_MS)
                takePicture(capture, file)
            }
            if (file.length() == 0L) throw IllegalStateException("The camera returned an empty image")
            file
        } catch (e: Exception) {
            file.delete()
            if (e is CancellationException) throw e
            throw IllegalStateException("Camera failed: ${e.message}. Android may block the camera while the app is in the background.", e)
        } finally {
            provider.unbind(capture)
            owner.destroy()
        }
    }

    private suspend fun takePicture(capture: ImageCapture, file: File): Unit = suspendCancellableCoroutine { cont ->
        capture.takePicture(
            ImageCapture.OutputFileOptions.Builder(file).build(),
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) { cont.resume(Unit) }
                override fun onError(exception: ImageCaptureException) { cont.resumeWithException(exception) }
            }
        )
    }

    @SuppressLint("MissingPermission")
    private suspend fun location(high: Boolean): StepOutcome {
        val client = LocationServices.getFusedLocationProviderClient(context)
        val token = CancellationTokenSource()
        val request = CurrentLocationRequest.Builder()
            .setPriority(if (high) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY)
            .setMaxUpdateAgeMillis(MAX_AGE_MS)
            .setDurationMillis(LOCATION_TIMEOUT_MS)
            .build()
        val location = try {
            client.getCurrentLocation(request, token.token).await()
        } finally {
            token.cancel()
        } ?: return StepOutcome.Failed("No location fix. Is location turned on?")
        val lat = "%.6f".format(Locale.US, location.latitude)
        val lng = "%.6f".format(Locale.US, location.longitude)
        return StepOutcome.Done(
            "$lat, $lng (±${location.accuracy.toInt()} m)",
            mapOf(
                "location.lat" to lat,
                "location.lng" to lng,
                "location.maps_url" to "https://maps.google.com/?q=$lat,$lng"
            )
        )
    }

    /** CameraX needs a lifecycle; this one lives for a single capture. */
    private class OneShotLifecycle : LifecycleOwner {
        private val registry = LifecycleRegistry(this).apply { currentState = Lifecycle.State.RESUMED }
        override val lifecycle: Lifecycle get() = registry
        fun destroy() { registry.currentState = Lifecycle.State.DESTROYED }
    }

    companion object {
        private const val WARM_UP_MS = 600L
        private const val MAX_AGE_MS = 60_000L
        private const val LOCATION_TIMEOUT_MS = 20_000L
        private val camera = Mutex()

        fun photoDir(context: Context): File = File(context.filesDir, "photos").apply { mkdirs() }
    }
}
