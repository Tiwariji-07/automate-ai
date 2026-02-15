package com.devfest.automation.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.devfest.automation.ui.theme.AgentTheme

class MainActivity : ComponentActivity() {
    
    private val requestPermissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Handle logic if needed, for now just log or proceed
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Request permissions on launch for demo functionality
        requestPermissions()
        
        setContent {
            AgentTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var currentScreen by remember { mutableStateOf<Screen>(Screen.Dashboard) }

                    when (currentScreen) {
                        Screen.Dashboard -> DashboardScreen(
                            viewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
                            onNavigateToChat = { currentScreen = Screen.Chat }
                        )
                        Screen.Chat -> ChatScreen(
                            onBack = { currentScreen = Screen.Dashboard },
                            onNavigateToEditor = { flowId ->
                                currentScreen = Screen.FlowEditor(flowId)
                            }
                        )
                        is Screen.FlowEditor -> FlowEditorScreen(
                            flowId = (currentScreen as Screen.FlowEditor).flowId,
                            viewModel = androidx.lifecycle.viewmodel.compose.viewModel(), // Shared VM would be better, but this works if VM is scoped to Activity
                            onBack = { currentScreen = Screen.Chat },
                            onDeploy = { 
                                // In a real app, we'd enable the flow in DB here.
                                // For now, we assume it's "deployed" and go to dashboard.
                                currentScreen = Screen.Dashboard 
                            }
                        )
                    }
                }
            }
        }
    }
    
    private fun requestPermissions() {
        val permissions = mutableListOf(
            android.Manifest.permission.CAMERA,
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION,
            android.Manifest.permission.SEND_SMS
        )
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            permissions.add(android.Manifest.permission.ACTIVITY_RECOGNITION)
        }
        
        requestPermissionLauncher.launch(permissions.toTypedArray())
    }
}

sealed class Screen {
    object Dashboard : Screen()
    object Chat : Screen()
    data class FlowEditor(val flowId: String) : Screen()
}
