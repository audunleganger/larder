package com.caloriecompanion.android

import android.app.Application

class CalorieApp : Application() {
    lateinit var state: AppState
        private set

    override fun onCreate() {
        super.onCreate()
        state = AppState(this)
    }
}
