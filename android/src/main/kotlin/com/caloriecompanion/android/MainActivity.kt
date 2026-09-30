package com.caloriecompanion.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import com.caloriecompanion.android.ui.CalorieCompanionTheme
import com.caloriecompanion.android.ui.LocalAppState
import com.caloriecompanion.android.ui.MainScreen
import com.caloriecompanion.android.ui.SetupScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val state = (application as CalorieApp).state
        setContent {
            CalorieCompanionTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    CompositionLocalProvider(LocalAppState provides state) {
                        if (state.repository == null) SetupScreen() else MainScreen()
                    }
                }
            }
        }
    }
}
