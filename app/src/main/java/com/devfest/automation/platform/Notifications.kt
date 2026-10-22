package com.devfest.automation.platform

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import java.util.concurrent.atomic.AtomicInteger

object Notifications {
    /** Notifications that flows post with the notify block. */
    const val FLOWS = "flows"
    /** The quiet ongoing notification while a background run or trigger listener is active. */
    const val SERVICE = "service"

    private val ids = AtomicInteger(1000)

    fun nextId(): Int = ids.incrementAndGet()

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(FLOWS, "Flow notifications", NotificationManager.IMPORTANCE_HIGH),
                NotificationChannel(SERVICE, "Background triggers", NotificationManager.IMPORTANCE_MIN).apply {
                    setShowBadge(false)
                }
            )
        )
    }
}
