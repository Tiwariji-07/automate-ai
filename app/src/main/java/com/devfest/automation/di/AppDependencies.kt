package com.devfest.automation.di

import android.content.Context
import com.devfest.agentclient.AgentClient
import com.devfest.agentclient.SessionTokenStore
import com.devfest.agentclient.model.IntentContext
import com.devfest.data.local.AutomationDatabase
import com.devfest.data.repository.DefaultFlowRepository
import com.devfest.data.repository.FlowRepository
import kotlinx.serialization.json.Json

object AppDependencies {

    @Volatile
    private var repository: FlowRepository? = null

    fun provideFlowRepository(context: Context): FlowRepository {
        return repository ?: synchronized(this) {
             repository ?: createRepository(context).also { repository = it }
        }
    }

    private fun createRepository(context: Context): FlowRepository {
        val json = Json {
            ignoreUnknownKeys = true
            prettyPrint = false
            encodeDefaults = true
        }
        val database = AutomationDatabase.build(context)

        val baseUrl = com.devfest.automation.BuildConfig.AGENT_BASE_URL

        val agentClient = AgentClient.build(
            context = context,
            baseUrl = baseUrl,
            json = json
        )
        ensureSessionToken(context)
        return DefaultFlowRepository(
            agentClient = agentClient,
            flowDao = database.flowDao(),
            json = json
        )
    }

    fun defaultIntentContext(): IntentContext = IntentContext(
        capabilities = listOf("LOCATION", "HTTP", "NOTIFICATION"),
        metadata = emptyMap()
    )

    private fun ensureSessionToken(context: Context) {
        val store = SessionTokenStore(context)
        if (store.getToken().isEmpty()) {
            store.setToken("demo-session-token")
        }
    }
}
