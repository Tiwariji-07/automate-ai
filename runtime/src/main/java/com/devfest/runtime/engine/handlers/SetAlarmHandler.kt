package com.devfest.runtime.engine.handlers

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import com.devfest.runtime.engine.FlowBlockHandler
import com.devfest.runtime.engine.FlowExecutionInput
import com.devfest.runtime.engine.FlowExecutionState
import com.devfest.runtime.engine.FlowStepResult
import com.devfest.runtime.engine.FlowStepStatus
import com.devfest.runtime.model.FlowBlock

class SetAlarmHandler(
    private val context: Context
) : FlowBlockHandler {
    override suspend fun handle(
        block: FlowBlock,
        input: FlowExecutionInput,
        state: FlowExecutionState
    ): FlowStepResult {
        val hour = block.params["hour"]?.toIntOrNull() ?: 7
        val minute = block.params["minute"]?.toIntOrNull() ?: 0
        val message = block.params["message"] ?: "Automation Alarm"
        val skipUi = block.params["skipUi"]?.toBoolean() ?: true

        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            putExtra(AlarmClock.EXTRA_MESSAGE, message)
            putExtra(AlarmClock.EXTRA_SKIP_UI, skipUi)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return try {
            context.startActivity(intent)
            FlowStepResult(block.id, FlowStepStatus.SUCCESS, "Alarm set for $hour:$minute")
        } catch (ex: Exception) {
            FlowStepResult(block.id, FlowStepStatus.FAILED, "Failed to set alarm: ${ex.localizedMessage}")
        }
    }
}
