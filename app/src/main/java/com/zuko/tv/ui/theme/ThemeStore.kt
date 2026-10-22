package com.zuko.tv.ui.theme

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Remembers the chosen mode (dark by default). */
class ThemeStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("zuko_theme", Context.MODE_PRIVATE)

    private val _dark = MutableStateFlow(prefs.getBoolean(KEY_DARK, true))
    val dark: StateFlow<Boolean> = _dark.asStateFlow()

    fun set(value: Boolean) {
        _dark.value = value
        prefs.edit().putBoolean(KEY_DARK, value).apply()
    }

    fun toggle() = set(!_dark.value)

    private companion object {
        const val KEY_DARK = "dark"
    }
}
