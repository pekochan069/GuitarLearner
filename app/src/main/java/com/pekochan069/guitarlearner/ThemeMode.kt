package com.pekochan069.guitarlearner

import android.content.SharedPreferences
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate

enum class ThemeMode(@param:StringRes val label: Int, val nightMode: Int) {
    System(R.string.theme_system, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM),
    Light(R.string.theme_light, AppCompatDelegate.MODE_NIGHT_NO),
    Dark(R.string.theme_dark, AppCompatDelegate.MODE_NIGHT_YES);

    companion object {
        fun read(preferences: SharedPreferences): ThemeMode =
            entries.firstOrNull { it.name == preferences.getString("theme_mode", null) } ?: System
    }
}
