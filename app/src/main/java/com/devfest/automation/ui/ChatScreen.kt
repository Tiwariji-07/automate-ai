package com.devfest.automation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.devfest.automation.ui.components.FlowPreview
import com.devfest.automation.ui.model.ChatMessage
import com.devfest.automation.ui.model.Role
import com.devfest.automation.viewmodel.AgentViewModel
import com.devfest.runtime.engine.FlowStepStatus

@Composable
fun ChatScreen(
    viewModel: AgentViewModel = viewModel(),
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Chat Header
        ChatHeader(onBack = onBack)

        // Chat List
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Welcome message if empty
            if (uiState.messages.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Describe an automation to get started.\ne.g., \"Turn on lights when I get home\"",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(top = 64.dp)
                        )
                    }
                }
            }

            items(uiState.messages) { message ->
                ChatBubble(
                    message = message,
                    viewModel = viewModel,
                    isExecuting = uiState.execution.isNotEmpty() && message.flowId == uiState.executingFlowId,
                    executionSteps = if (message.flowId == uiState.executingFlowId) uiState.execution else emptyList()
                )
            }

            if (uiState.isLoading) {
                item {
                    Row(
                        modifier = Modifier.padding(start = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Thinking...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }
        }

        // Input Area with Pills
        Column(
             modifier = Modifier
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
        ) {
            SuggestionPills(onSuggestionClick = { viewModel.sendMessage(it) })
            ChatInput(
                enabled = !uiState.isLoading,
                onSend = viewModel::sendMessage
            )
        }
    }
}

@Composable
fun ChatHeader(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                text = "Agent",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Online",
                style = MaterialTheme.typography.labelSmall,
                color = com.devfest.automation.ui.theme.ActionGreen
            )
        }
    }
}

@Composable
fun SuggestionPills(onSuggestionClick: (String) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val suggestions = listOf(
            "Battery Saver Mode",
            "Morning Briefing",
            "Save Photos to Drive",
            "Gym Playlist"
        )
        items(suggestions) { text ->
            SuggestionPill(text = text, onClick = { onSuggestionClick(text) })
        }
    }
}

@Composable
fun SuggestionPill(text: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = androidx.compose.foundation.shape.CircleShape,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        color = MaterialTheme.colorScheme.surface
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun ChatBubble(
    message: ChatMessage,
    viewModel: AgentViewModel,
    isExecuting: Boolean,
    executionSteps: List<com.devfest.runtime.engine.FlowStepResult>
) {
    val isUser = message.role == Role.USER
    val alignment = if (isUser) Alignment.End else Alignment.Start
    val containerColor = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer
    val contentColor = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer
    val shape = if (isUser) {
        RoundedCornerShape(20.dp, 4.dp, 20.dp, 20.dp)
    } else {
        RoundedCornerShape(4.dp, 20.dp, 20.dp, 20.dp)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Row(
            verticalAlignment = Alignment.Bottom
        ) {
            if (!isUser) {
                Icon(
                    imageVector = Icons.Filled.SmartToy,
                    contentDescription = "Agent",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(32.dp)
                        .padding(end = 8.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f), CircleShape)
                        .padding(4.dp)
                )
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = containerColor),
                shape = shape,
                modifier = Modifier.widthIn(max = 300.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = message.text,
                        color = contentColor,
                        style = MaterialTheme.typography.bodyMedium
                    )

                    // If this message has a flow attached, show the preview card
                    if (message.flowId != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        val graph = viewModel.uiState.value.flowGraphs[message.flowId]
                        if (graph != null) {
                            FlowCard(
                                graph = graph,
                                onRun = { viewModel.runFlow(graph) },
                                isExecuting = isExecuting && viewModel.uiState.value.execution.isNotEmpty() && viewModel.uiState.value.execution.firstOrNull()?.blockId != null, // simplified check
                                steps = executionSteps
                            )
                        } else {
                            // Show placeholder if graph data not in ViewState (history)
                            // Ideally we'd look this up from history, for now just show title
                            Text(
                                "Flow Graph ${message.flowId.take(4)}...",
                                style = MaterialTheme.typography.labelSmall,
                                color = contentColor.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FlowCard(
    graph: com.devfest.runtime.model.FlowGraph,
    onRun: () -> Unit,
    isExecuting: Boolean,
    steps: List<com.devfest.runtime.engine.FlowStepResult>
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "✨ Generated Flow",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = graph.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = graph.explanation,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Render the graph using Canvas view
            com.devfest.automation.ui.components.FlowGraphView(
                graph = graph,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp) // Fixed height for graph window
                    .clip(RoundedCornerShape(8.dp))
            )
            
            if (steps.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text("Execution Log:", style = MaterialTheme.typography.labelSmall)
                steps.forEach { step ->
                     val icon = when (step.status) {
                        FlowStepStatus.SUCCESS -> "✅"
                        FlowStepStatus.SKIPPED -> "⚠️"
                        FlowStepStatus.FAILED -> "❌"
                    }
                    Text(
                        text = "$icon ${step.blockId}: ${step.message}",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 10.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onRun,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isExecuting
            ) {
                if (isExecuting) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Running...")
                } else {
                    Text("Run Flow ▶")
                }
            }
        }
    }
}

@Composable
fun ChatInput(
    enabled: Boolean,
    onSend: (String) -> Unit
) {
    var text by remember { mutableStateOf("") }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Ask the agent...") },
                maxLines = 3,
                shape = RoundedCornerShape(24.dp),
                enabled = enabled
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = {
                    if (text.isNotBlank()) {
                        onSend(text)
                        text = ""
                    }
                },
                enabled = enabled && text.isNotBlank(),
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                    .size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}
