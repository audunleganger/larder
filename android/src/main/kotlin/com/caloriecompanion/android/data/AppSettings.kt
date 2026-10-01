package com.caloriecompanion.android.data

import android.content.Context
import androidx.core.content.edit

enum class Mode { LOCAL, SERVER }

/** Persisted app configuration: which mode, and the server session in server mode (A-6). */
class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var mode: Mode?
        get() = prefs.getString(KEY_MODE, null)?.let { runCatching { Mode.valueOf(it) }.getOrNull() }
        set(value) = prefs.edit { putString(KEY_MODE, value?.name) }

    var serverUrl: String?
        get() = prefs.getString(KEY_URL, null)
        set(value) = prefs.edit { putString(KEY_URL, value) }

    var token: String?
        get() = prefs.getString(KEY_TOKEN, null)
        set(value) = prefs.edit { putString(KEY_TOKEN, value) }

    var username: String?
        get() = prefs.getString(KEY_USER, null)
        set(value) = prefs.edit { putString(KEY_USER, value) }

    private companion object {
        const val KEY_MODE = "mode"
        const val KEY_URL = "serverUrl"
        const val KEY_TOKEN = "token"
        const val KEY_USER = "username"
    }
}
