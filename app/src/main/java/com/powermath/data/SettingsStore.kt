package com.powermath.data

import android.content.Context
import com.powermath.math.AngleMode
import com.powermath.math.FormatSettings
import com.powermath.math.ResultMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class ThemePref(val label: String) { SYSTEM("System"), DARK("Dark"), LIGHT("Light") }

data class AppSettings(
    val mode: ResultMode = ResultMode.EXACT,
    val decimals: Int = 10,
    val sigDigits: Int = 10,
    val angle: AngleMode = AngleMode.DEG,
    val theme: ThemePref = ThemePref.DARK,
    val haptics: Boolean = true
) {
    val format: FormatSettings get() = FormatSettings(mode, decimals, sigDigits)
}

/** Settings persisted in SharedPreferences and exposed as a StateFlow. */
class SettingsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("powermath_settings", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val flow: StateFlow<AppSettings> = _state

    private inline fun <reified E : Enum<E>> enumOf(key: String, default: E): E =
        runCatching { enumValueOf<E>(prefs.getString(key, default.name) ?: default.name) }.getOrDefault(default)

    private fun load() = AppSettings(
        mode = enumOf("mode", ResultMode.EXACT),
        decimals = prefs.getInt("decimals", 10).coerceIn(0, 50),
        sigDigits = prefs.getInt("sig", 10).coerceIn(1, 50),
        angle = enumOf("angle", AngleMode.DEG),
        theme = enumOf("theme", ThemePref.DARK),
        haptics = prefs.getBoolean("haptics", true)
    )

    fun update(transform: (AppSettings) -> AppSettings) {
        val s = transform(_state.value)
        _state.value = s
        prefs.edit()
            .putString("mode", s.mode.name).putInt("decimals", s.decimals).putInt("sig", s.sigDigits)
            .putString("angle", s.angle.name).putString("theme", s.theme.name).putBoolean("haptics", s.haptics)
            .apply()
    }
}
