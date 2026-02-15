package com.devfest.runtime.engine.blocks

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import com.devfest.runtime.engine.FlowBlockHandler
import com.devfest.runtime.engine.FlowExecutionInput
import com.devfest.runtime.engine.FlowExecutionState
import com.devfest.runtime.engine.FlowStepResult
import com.devfest.runtime.engine.FlowStepStatus
import com.devfest.runtime.model.FlowBlock
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.tasks.await

class GetLocationHandler(private val context: Context) : FlowBlockHandler {
    override suspend fun handle(
        block: FlowBlock,
        input: FlowExecutionInput,
        state: FlowExecutionState
    ): FlowStepResult {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) {
            return FlowStepResult(block.id, FlowStepStatus.FAILED, "Missing Location Permission")
        }

        return try {
            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
            
            val priorityParam = block.params["accuracy"] ?: "balanced"
            val priority = when(priorityParam.lowercase()) {
                "high" -> Priority.PRIORITY_HIGH_ACCURACY
                "low" -> Priority.PRIORITY_LOW_POWER
                else -> Priority.PRIORITY_BALANCED_POWER_ACCURACY
            }

            // Try to get last known location first for speed, or wait for a fresh one
            val location: Location? = fusedLocationClient.getLastLocation().await() ?: 
                                      fusedLocationClient.getCurrentLocation(priority, null).await()

            if (location != null) {
                // Store in variables for subsequent blocks to use
                state.variables["lat"] = location.latitude.toString()
                state.variables["lng"] = location.longitude.toString()
                FlowStepResult(block.id, FlowStepStatus.SUCCESS, "Loc: ${location.latitude}, ${location.longitude}")
            } else {
                FlowStepResult(block.id, FlowStepStatus.FAILED, "Location not available")
            }
        } catch (e: Exception) {
            FlowStepResult(block.id, FlowStepStatus.FAILED, "Loc Error: ${e.message}")
        }
    }
}
