package com.zuko.tv.ui.theme

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Remembers the chosen mode (dark by default). */
class ThemeStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("zuko_theme", Context.MODE_PRIVATE)

    private val _mode = MutableStateFlow(prefs.getString(KEY_MODE, "Dark") ?: "Dark")
    val mode: StateFlow<String> = _mode.asStateFlow()
    private val _dark = MutableStateFlow(_mode.value != "Light")
    val dark: StateFlow<Boolean> = _dark.asStateFlow()

    fun setMode(value: String) {
        _mode.value = value
        val darkNow = when (value) {
            "System" -> (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
            "Light" -> false
            else -> true
        }
        _dark.value = darkNow
        prefs.edit().putString(KEY_MODE, value).putBoolean(KEY_DARK, darkNow).apply()
    }

    fun set(value: Boolean) = setMode(if (value) "Dark" else "Light")
    fun toggle() = set(!_dark.value)

    private companion object { const val KEY_MODE = "mode"; const val KEY_DARK = "dark" }
}
