package com.devfest.automation.ui.model

import java.util.UUID

enum class Role {
    USER, AGENT
}

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: Role,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val flowId: String? = null // if this message contains a generated flow
)
