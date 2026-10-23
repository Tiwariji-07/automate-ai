package com.devfest.automation.ui

import android.content.Context
import com.devfest.automation.ui.theme.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class UiSettings(context: Context) {

    private val prefs = context.getSharedPreferences("ui", Context.MODE_PRIVATE)

    private val _theme = MutableStateFlow(
        prefs.getString(KEY_THEME, null)?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM
    )
    val theme: StateFlow<ThemeMode> = _theme.asStateFlow()

    fun setTheme(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME, mode.name).apply()
        _theme.value = mode
    }

    private companion object {
        const val KEY_THEME = "theme"
    }
}
