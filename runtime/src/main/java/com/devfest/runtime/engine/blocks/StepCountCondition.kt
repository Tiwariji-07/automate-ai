package com.devfest.runtime.engine.blocks

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Checks if the user has taken a certain number of steps within a timeframe.
 * For this hackathon demo, we simply check if the *total* step count increases by a threshold.
 */
class StepCountCondition(private val context: Context) {

    suspend fun check(threshold: Int = 10): Boolean {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

        if (stepSensor == null) return false // No sensor

        // Flow that emits step counts
        val stepFlow = callbackFlow {
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent?) {
                    event?.values?.firstOrNull()?.let { trySend(it) }
                }
                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
            }
            sensorManager.registerListener(listener, stepSensor, SensorManager.SENSOR_DELAY_UI)
            awaitClose { sensorManager.unregisterListener(listener) }
        }

        // Wait for 5 seconds to see if steps increase
        // In a real app, this would be a background service monitoring over time.
        // For demo: "Walk 5 steps now to trigger"
        val startSteps = withTimeoutOrNull(1000) { stepFlow.first() } ?: return false
        
        // Wait for updated steps
        val endSteps = withTimeoutOrNull(10000) { 
             stepFlow.first { it >= startSteps + threshold }
        }

        return endSteps != null
    }
}
