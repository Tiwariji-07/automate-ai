package com.devfest.automation.platform

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.devfest.automation.ai.AiSettings
import com.devfest.automation.trigger.UnlockWatcher
import com.devfest.core.catalog.Requirement

/** How the app checks and asks for each [Requirement]. */
object Requirements {

    /** Runtime permission to request with the system dialog, or null when a settings screen is needed. */
    fun permission(requirement: Requirement): String? = when (requirement) {
        Requirement.NOTIFICATIONS -> Manifest.permission.POST_NOTIFICATIONS
        Requirement.SEND_SMS -> Manifest.permission.SEND_SMS
        Requirement.RECEIVE_SMS -> Manifest.permission.RECEIVE_SMS
        Requirement.CAMERA -> Manifest.permission.CAMERA
        Requirement.LOCATION -> Manifest.permission.ACCESS_FINE_LOCATION
        Requirement.BACKGROUND_LOCATION -> Manifest.permission.ACCESS_BACKGROUND_LOCATION
        else -> null
    }

    fun isGranted(context: Context, requirement: Requirement): Boolean {
        permission(requirement)?.let { permission ->
            val granted = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
            return if (requirement == Requirement.NOTIFICATIONS) {
                granted && NotificationManagerCompat.from(context).areNotificationsEnabled()
            } else {
                granted
            }
        }
        return when (requirement) {
            Requirement.EXACT_ALARMS -> context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
            Requirement.DEVICE_ADMIN -> context.getSystemService(DevicePolicyManager::class.java)
                .isAdminActive(ComponentName(context, UnlockWatcher::class.java))
            Requirement.NOTIFICATION_ACCESS -> context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)
            Requirement.DND_ACCESS -> context.getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted
            Requirement.WRITE_SETTINGS -> Settings.System.canWrite(context)
            Requirement.OVERLAY -> Settings.canDrawOverlays(context)
            // Vivo kills background apps that are battery-optimized, so event triggers need the exemption.
            Requirement.LISTENER_SERVICE -> context.getSystemService(PowerManager::class.java)
                .isIgnoringBatteryOptimizations(context.packageName)
            Requirement.AI_KEY -> AiSettings(context).load().isComplete
            else -> false
        }
    }

    fun missing(context: Context, requirements: Collection<Requirement>): List<Requirement> =
        requirements.filterNot { isGranted(context, it) }

    /** Settings screen for requirements that have no runtime dialog. */
    fun settingsIntent(context: Context, requirement: Requirement): Intent? {
        val pkg = Uri.parse("package:${context.packageName}")
        return when (requirement) {
            Requirement.EXACT_ALARMS -> Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkg)
            Requirement.DEVICE_ADMIN -> Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, ComponentName(context, UnlockWatcher::class.java))
                .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Lets flows run when someone enters a wrong PIN or pattern.")
            Requirement.NOTIFICATION_ACCESS -> Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            Requirement.DND_ACCESS -> Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
            Requirement.WRITE_SETTINGS -> Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, pkg)
            Requirement.OVERLAY -> Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, pkg)
            Requirement.LISTENER_SERVICE -> Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkg)
            Requirement.NOTIFICATIONS -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            else -> null
        }
    }

    fun label(requirement: Requirement): String = when (requirement) {
        Requirement.NOTIFICATIONS -> "Notifications"
        Requirement.SEND_SMS -> "Send SMS"
        Requirement.RECEIVE_SMS -> "Read incoming SMS"
        Requirement.CAMERA -> "Camera"
        Requirement.LOCATION -> "Location"
        Requirement.BACKGROUND_LOCATION -> "Location all the time"
        Requirement.EXACT_ALARMS -> "Alarms & reminders"
        Requirement.DEVICE_ADMIN -> "Device admin"
        Requirement.NOTIFICATION_ACCESS -> "Notification access"
        Requirement.DND_ACCESS -> "Do Not Disturb access"
        Requirement.WRITE_SETTINGS -> "Modify system settings"
        Requirement.OVERLAY -> "Display over other apps"
        Requirement.LISTENER_SERVICE -> "No battery restrictions"
        Requirement.AI_KEY -> "AI API key"
    }
}
