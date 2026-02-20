package com.devfest.automation.ui

import android.app.Activity
import android.content.pm.PackageManager
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.devfest.automation.ui.theme.ActionGreen
import com.devfest.automation.ui.theme.ElectricBlue
import com.devfest.automation.util.DeviceAdminHelper
import com.devfest.automation.util.FlowPermissionHelper
import com.devfest.runtime.model.BlockType

// ─── Theme-Aware Colors (adapt to light/dark) ───
private val Indigo500 = Color(0xFF6366F1)
private val Green600 = Color(0xFF059669)
private val Amber600 = Color(0xFFD97706)
private val Rose500 = Color(0xFFF43F5E)

// These are used inline via MaterialTheme.colorScheme for adaptive theming:
// CardBg        → MaterialTheme.colorScheme.surface
// SubtleBorder  → MaterialTheme.colorScheme.outlineVariant
// Accent bg     → accent.copy(alpha = 0.12f) (works on both light/dark)

@Composable
fun DashboardScreen(
    viewModel: com.devfest.automation.viewmodel.AgentViewModel,
    onNavigateToChat: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            DashboardHeader()

            LazyColumn(
                contentPadding = PaddingValues(bottom = 100.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                item { StatsSection(viewModel) }

                item {
                    SectionHeader(title = "Your Flows")
                    ActiveFlowsList(viewModel)
                }
            }
        }

        // Extended FAB with label
        ExtendedFloatingActionButton(
            onClick = onNavigateToChat,
            containerColor = ElectricBlue,
            contentColor = Color.White,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .shadow(
                    elevation = 12.dp,
                    shape = RoundedCornerShape(16.dp),
                    ambientColor = ElectricBlue.copy(alpha = 0.3f),
                    spotColor = ElectricBlue.copy(alpha = 0.3f)
                ),
            shape = RoundedCornerShape(16.dp),
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text("New Flow", fontWeight = FontWeight.SemiBold) }
        )
    }
}

// ─── HEADER ───
@Composable
private fun DashboardHeader() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Automate AI",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Your intelligent automation hub",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // App icon
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(ElectricBlue, Indigo500)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res.painterResource(id = com.devfest.automation.R.drawable.ic_launcher_foreground),
                    contentDescription = "App Icon",
                    modifier = Modifier.size(36.dp)
                )
            }
        }
    }
}

// ─── STATS ───
@Composable
private fun StatsSection(viewModel: com.devfest.automation.viewmodel.AgentViewModel) {
    val uiState = viewModel.uiState.collectAsState().value
    val activeCount = uiState.activeFlowIds.size
    val totalFlows = uiState.flowGraphs.size

    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(bottom = 20.dp)
    ) {
        item {
            StatCard(
                label = "Active Flows",
                value = "$activeCount",
                accent = Green600,
                icon = Icons.Filled.CheckCircle
            )
        }
        item {
            StatCard(
                label = "Total Created",
                value = "$totalFlows",
                accent = Indigo500,
                icon = Icons.Filled.Bolt
            )
        }
        item {
            StatCard(
                label = "Time Saved",
                value = "${(activeCount * 0.5).let { if (it == 0.0) "-" else "${it}h" }}",
                accent = Amber600,
                icon = Icons.Filled.Timer
            )
        }
    }
}

@Composable
private fun StatCard(
    label: String,
    value: String,
    accent: Color,
    icon: ImageVector
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .width(150.dp)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Icon badge
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ─── SECTION HEADER ───
@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
    )
}

// ─── FLOW LIST ───
@Composable
fun ActiveFlowsList(viewModel: com.devfest.automation.viewmodel.AgentViewModel) {
    val uiState = viewModel.uiState.collectAsState().value
    val context = LocalContext.current
    val activity = context as? Activity
    var pendingActivateFlowId by remember { mutableStateOf<String?>(null) }
    var pendingActivateNeedsCamera by remember { mutableStateOf(false) }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (pendingActivateFlowId != null) {
            if (granted) {
                viewModel.toggleFlow(pendingActivateFlowId!!, true)
            }
            pendingActivateFlowId = null
            pendingActivateNeedsCamera = false
        }
    }

    val deviceAdminLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && pendingActivateFlowId != null) {
            val flowId = pendingActivateFlowId!!
            val needsCamera = pendingActivateNeedsCamera &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
            if (needsCamera) {
                cameraLauncher.launch(Manifest.permission.CAMERA)
            } else {
                viewModel.toggleFlow(flowId, true)
                pendingActivateFlowId = null
                pendingActivateNeedsCamera = false
            }
        } else {
            pendingActivateFlowId = null
            pendingActivateNeedsCamera = false
        }
    }

    fun onToggleChecked(graph: com.devfest.runtime.model.FlowGraph, checked: Boolean) {
        if (!checked) {
            viewModel.toggleFlow(graph.id, false)
            return
        }

        val needsDeviceAdmin = FlowPermissionHelper.requiresDeviceAdmin(graph)
        val needsCamera = FlowPermissionHelper.requiresCamera(graph)
        val hasCamera = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

        if (needsDeviceAdmin && activity == null) return

        if (!needsDeviceAdmin) {
            if (needsCamera && !hasCamera) {
                if (activity != null) {
                    pendingActivateFlowId = graph.id
                    pendingActivateNeedsCamera = true
                    cameraLauncher.launch(Manifest.permission.CAMERA)
                }
                return
            }
            viewModel.toggleFlow(graph.id, true)
            return
        }

        if (!DeviceAdminHelper.isDeviceAdminEnabled(context)) {
            pendingActivateFlowId = graph.id
            pendingActivateNeedsCamera = needsCamera && !hasCamera
            deviceAdminLauncher.launch(DeviceAdminHelper.createAddDeviceAdminIntent(context))
            return
        }

        if (needsCamera && !hasCamera && activity != null) {
            pendingActivateFlowId = graph.id
            pendingActivateNeedsCamera = true
            cameraLauncher.launch(Manifest.permission.CAMERA)
        } else {
            viewModel.toggleFlow(graph.id, true)
        }
    }

    Column(
        modifier = Modifier.padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (uiState.flowGraphs.isEmpty()) {
            EmptyFlowsState()
        } else {
            uiState.flowGraphs.values.reversed().forEach { graph ->
                val isActive = uiState.activeFlowIds.contains(graph.id)
                FlowStatusCard(
                    graph = graph,
                    isActive = isActive,
                    onToggle = { checked -> onToggleChecked(graph, checked) },
                    showRunButton = graph.blocks.any { it.type == BlockType.MANUAL_QUICK_TRIGGER },
                    onRun = { viewModel.runFlow(graph) }
                )
            }
        }
    }
}

// ─── EMPTY STATE ───
@Composable
private fun EmptyFlowsState() {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Indigo500.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Bolt,
                    contentDescription = null,
                    tint = Indigo500,
                    modifier = Modifier.size(32.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No flows yet",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Tap \"New Flow\" to create your first automation",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ─── FLOW CARD ───
@Composable
private fun FlowStatusCard(
    graph: com.devfest.runtime.model.FlowGraph,
    isActive: Boolean,
    onToggle: (Boolean) -> Unit,
    showRunButton: Boolean = false,
    onRun: () -> Unit = {}
) {
    // Determine icon based on trigger type
    val triggerBlock = graph.blocks.firstOrNull {
        it.type.category == com.devfest.runtime.model.BlockCategory.TRIGGER
    }
    val (flowIcon, iconTint) = when (triggerBlock?.type) {
        BlockType.PATTERN_FAILURE_TRIGGER -> Pair(Icons.Filled.Lock, Rose500)
        BlockType.TIME_SCHEDULE_TRIGGER -> Pair(Icons.Filled.Schedule, ElectricBlue)
        BlockType.LOCATION_EXIT_TRIGGER -> Pair(Icons.Filled.LocationOn, Green600)
        BlockType.MANUAL_QUICK_TRIGGER -> Pair(Icons.Filled.PlayArrow, Indigo500)
        else -> Pair(Icons.Filled.Bolt, Indigo500)
    }

    val statusDotColor by animateColorAsState(
        targetValue = if (isActive) ElectricBlue else MaterialTheme.colorScheme.outlineVariant,
        animationSpec = tween(300)
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = if (isActive) ElectricBlue.copy(alpha = 0.3f) else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(16.dp)
            )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    // Icon badge
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(iconTint.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(flowIcon, contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = graph.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(statusDotColor)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isActive) "Active" else "Inactive",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isActive) ElectricBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (showRunButton) {
                        androidx.compose.material3.IconButton(onClick = onRun) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Run Now",
                                tint = ElectricBlue
                            )
                        }
                    }
                    Switch(
                        checked = isActive,
                        onCheckedChange = onToggle,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                            checkedTrackColor = ElectricBlue,
                            uncheckedThumbColor = MaterialTheme.colorScheme.outlineVariant,
                            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                }
            }

            // Description
            if (graph.explanation.isNotBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = graph.explanation,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 18.sp
                )
            }

            // Block pills
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                graph.blocks.take(4).forEach { block ->
                    BlockPill(block.type)
                }
                if (graph.blocks.size > 4) {
                    Text(
                        text = "+${graph.blocks.size - 4}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

// ─── BLOCK PILL ───
@Composable
private fun BlockPill(type: BlockType) {
    val label = when (type) {
        BlockType.PATTERN_FAILURE_TRIGGER -> "🔒 Pattern Fail"
        BlockType.TIME_SCHEDULE_TRIGGER -> "⏰ Schedule"
        BlockType.LOCATION_EXIT_TRIGGER -> "📍 Location"
        BlockType.MANUAL_QUICK_TRIGGER -> "▶ Manual"
        BlockType.CAMERA -> "📸 Camera"
        BlockType.SEND_SMS_ACTION -> "💬 SMS"
        BlockType.SEND_NOTIFICATION_ACTION -> "🔔 Notify"
        BlockType.TOGGLE_WIFI_ACTION -> "📶 WiFi"
        BlockType.SET_ALARM_ACTION -> "⏰ Alarm"
        BlockType.PLAY_SOUND_ACTION -> "🔊 Sound"
        BlockType.DELAY_ACTION -> "⏳ Delay"
        BlockType.BATTERY_LEVEL_CONDITION -> "🔋 Battery"
        BlockType.TIME_WINDOW_CONDITION -> "🕐 Time"
        else -> "⚙ ${type.name.take(8)}"
    }

    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 11.sp,
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
}
