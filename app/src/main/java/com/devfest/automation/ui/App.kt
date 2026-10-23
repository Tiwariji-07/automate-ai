package com.devfest.automation.ui

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.devfest.automation.AutomateApp
import com.devfest.automation.store.FlowStatus
import kotlinx.serialization.Serializable

@Serializable object SetupRoute
@Serializable object HomeRoute
@Serializable object SettingsRoute
@Serializable data class ChatRoute(val flowId: String? = null, val prompt: String? = null)
@Serializable data class ReviewRoute(val flowId: String)
@Serializable data class FlowRoute(val flowId: String)

@Composable
fun appInstance(): AutomateApp = LocalContext.current.applicationContext as AutomateApp

@Composable
fun AppNav() {
    val app = appInstance()
    val nav = rememberNavController()
    val start: Any = remember { if (app.aiSettings.load().isComplete) HomeRoute else SetupRoute }

    NavHost(
        nav, start,
        enterTransition = { slideInHorizontally { it / 5 } + fadeIn() },
        exitTransition = { fadeOut() },
        popEnterTransition = { fadeIn() },
        popExitTransition = { slideOutHorizontally { it / 5 } + fadeOut() }
    ) {
        composable<SetupRoute> {
            SetupScreen(onDone = { nav.navigate(HomeRoute) { popUpTo<SetupRoute> { inclusive = true } } })
        }
        composable<HomeRoute> {
            HomeScreen(
                onOpenFlow = { flow ->
                    if (flow.status == FlowStatus.DRAFT) nav.navigate(ChatRoute(flow.id)) else nav.navigate(FlowRoute(flow.id))
                },
                onReview = { nav.navigate(ReviewRoute(it)) },
                onNewChat = { nav.navigate(ChatRoute(prompt = it)) },
                onSettings = { nav.navigate(SettingsRoute) }
            )
        }
        composable<ChatRoute> { entry ->
            val route = entry.toRoute<ChatRoute>()
            ChatScreen(route.flowId, route.prompt, onBack = { nav.popBackStack() }, onReview = { nav.navigate(ReviewRoute(it)) })
        }
        composable<ReviewRoute> { entry ->
            val id = entry.toRoute<ReviewRoute>().flowId
            ReviewScreen(
                id,
                onBack = { nav.popBackStack() },
                onTestStarted = { nav.navigate(FlowRoute(id)) },
                onTurnedOn = { nav.popBackStack(HomeRoute, inclusive = false) }
            )
        }
        composable<FlowRoute> { entry ->
            val id = entry.toRoute<FlowRoute>().flowId
            FlowScreen(
                id,
                onBack = { nav.popBackStack() },
                onEdit = { nav.navigate(ChatRoute(id)) },
                onReview = { nav.navigate(ReviewRoute(id)) }
            )
        }
        composable<SettingsRoute> { SettingsScreen(onBack = { nav.popBackStack() }) }
    }
}
