package com.caloriecompanion.android.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val Blue = Color(0xFF2A78D6)
private val BlueDark = Color(0xFF3987E5)

/** Status colors from the web app's palette; always paired with an icon and a label. */
object StatusColors {
    val good = Color(0xFF0CA30C)
    val serious = Color(0xFFEC835A)
    val warning = Color(0xFFFAB219)
    val series1Light = Blue
    val series1Dark = BlueDark
    val series2Light = Color(0xFFEB6834)
    val series2Dark = Color(0xFFD95926)
}

@Composable
fun CalorieCompanionTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme(primary = BlueDark)
        else -> lightColorScheme(primary = Blue)
    }
    MaterialTheme(colorScheme = colors, content = content)
}
