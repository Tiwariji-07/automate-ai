package com.devfest.runtime.engine.blocks

import android.content.Context
import com.devfest.runtime.engine.FlowBlockHandler
import com.devfest.runtime.engine.FlowExecutionInput
import com.devfest.runtime.engine.FlowExecutionState
import com.devfest.runtime.engine.FlowStepResult
import com.devfest.runtime.engine.FlowStepStatus
import com.devfest.runtime.model.FlowBlock

class StepCountHandler(private val context: Context) : FlowBlockHandler {
    private val logic = StepCountCondition(context)

    override suspend fun handle(
        block: FlowBlock,
        input: FlowExecutionInput,
        state: FlowExecutionState
    ): FlowStepResult {
        val threshold = block.params["threshold"]?.toIntOrNull() ?: 5
        val success = logic.check(threshold)
        
        return if (success) {
            FlowStepResult(block.id, FlowStepStatus.SUCCESS, "Walked $threshold steps")
        } else {
            FlowStepResult(block.id, FlowStepStatus.SKIPPED, "Did not detect $threshold steps")
        }
    }
}
