package com.larder.android

import android.app.Application

class LarderApp : Application() {
    lateinit var state: AppState
        private set

    override fun onCreate() {
        super.onCreate()
        state = AppState(this)
    }
}
