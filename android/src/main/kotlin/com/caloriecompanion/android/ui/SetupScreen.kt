package com.caloriecompanion.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.caloriecompanion.android.R
import com.caloriecompanion.android.data.RepositoryException
import kotlinx.coroutines.launch

/** First launch (and after a session expires): choose local or server mode (A-6). */
@Composable
fun SetupScreen() {
    val app = LocalAppState.current
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf(app.settings.serverUrl ?: "") }
    var username by remember { mutableStateOf(app.settings.username ?: "") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Throwable?>(null) }

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.welcome_title), style = MaterialTheme.typography.headlineMedium)
        if (app.sessionExpired) {
            Text(stringResource(R.string.setup_session_expired), color = MaterialTheme.colorScheme.error)
        }
        SectionCard(stringResource(R.string.mode_local)) {
            Muted(stringResource(R.string.mode_local_description))
            Button(onClick = { app.useLocal() }) { Text(stringResource(R.string.setup_use_local)) }
        }
        SectionCard(stringResource(R.string.mode_server)) {
            Muted(stringResource(R.string.mode_server_description))
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text(stringResource(R.string.setup_server_url)) },
                placeholder = { Text("http://192.168.1.10:8080") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            if (url.isNotBlank() && !url.trim().startsWith("https://", ignoreCase = true)) {
                // O-3: plain HTTP is allowed for LAN servers, but the user should know.
                Text(stringResource(R.string.setup_http_warning), color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.bodySmall)
            }
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text(stringResource(R.string.login_username)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(stringResource(R.string.login_password)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            ErrorText(error)
            Button(
                enabled = !busy && url.isNotBlank() && username.isNotBlank() && password.isNotEmpty(),
                onClick = {
                    busy = true
                    error = null
                    scope.launch {
                        try {
                            app.connect(url, username, password)
                        } catch (e: RepositoryException) {
                            error = e
                        } finally {
                            busy = false
                        }
                    }
                },
            ) { Text(stringResource(R.string.setup_connect)) }
        }
        Muted(stringResource(R.string.setup_switch_hint))
    }
}
