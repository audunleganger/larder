package com.caloriecompanion.android.ui

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.caloriecompanion.android.R
import com.caloriecompanion.android.data.Mode
import com.caloriecompanion.android.data.RepositoryException
import com.caloriecompanion.android.data.apiJson
import com.caloriecompanion.shared.AppInfo
import com.caloriecompanion.shared.api.ConflictStrategy
import com.caloriecompanion.shared.api.ExportFile
import com.caloriecompanion.shared.api.ImportResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

@Composable
private fun PasswordSection() {
    val app = LocalAppState.current
    val scope = rememberCoroutineScope()
    var current by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<Throwable?>(null) }
    var done by remember { mutableStateOf(false) }
    SectionCard(stringResource(R.string.settings_password)) {
        OutlinedTextField(current, { current = it }, label = { Text(stringResource(R.string.settings_current_password)) }, singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(new, { new = it }, label = { Text(stringResource(R.string.settings_new_password)) }, singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())
        ErrorText(error)
        if (done) Text("✓ " + stringResource(R.string.settings_password_changed), color = StatusColors.good)
        Button(enabled = current.isNotEmpty() && new.length >= 8, onClick = {
            error = null
            done = false
            scope.launch {
                try {
                    app.remote?.changePassword(current, new)
                    current = ""
                    new = ""
                    done = true
                } catch (e: RepositoryException) {
                    error = e
                }
            }
        }) { Text(stringResource(R.string.settings_change_password)) }
    }
}

@Composable
private fun TransferSection() {
    val app = LocalAppState.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<Throwable?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var strategy by remember { mutableStateOf(ConflictStrategy.SKIP) }
    var result by remember { mutableStateOf<ImportResult?>(null) }
    val exported = stringResource(R.string.settings_exported)
    val invalidFile = stringResource(R.string.settings_invalid_file)

    // X-1: export via the system file picker.
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val repo = app.repository ?: return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val data = apiJson.encodeToString(ExportFile.serializer(), repo.export())
                withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.use { it.write(data.toByteArray()) } }
                message = exported
            } catch (e: RepositoryException) {
                error = e
            }
        }
    }
    // X-2/X-3: import from a file.
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val repo = app.repository ?: return@rememberLauncherForActivityResult
        scope.launch {
            error = null
            message = null
            val text = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } }
            val file = text?.let { runCatching { apiJson.decodeFromString(ExportFile.serializer(), it) }.getOrNull() }
            if (file == null) {
                message = invalidFile
                return@launch
            }
            try {
                result = repo.import(file, strategy)
                app.changed()
            } catch (e: RepositoryException) {
                error = e
            }
        }
    }

    SectionCard(stringResource(R.string.settings_export)) {
        Muted(stringResource(R.string.settings_export_hint))
        OutlinedButton(onClick = { exportLauncher.launch("calorie-companion-${LocalDate.now()}.json") }) { Text(stringResource(R.string.settings_download)) }
    }
    SectionCard(stringResource(R.string.settings_import)) {
        Muted(stringResource(R.string.settings_import_hint))
        Text(stringResource(R.string.settings_on_conflict), style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(strategy == ConflictStrategy.SKIP, { strategy = ConflictStrategy.SKIP }, label = { Text(stringResource(R.string.settings_strategy_skip)) })
            FilterChip(strategy == ConflictStrategy.OVERWRITE, { strategy = ConflictStrategy.OVERWRITE }, label = { Text(stringResource(R.string.settings_strategy_overwrite)) })
        }
        OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }) { Text(stringResource(R.string.settings_import_button)) }
        result?.let { r ->
            listOf(
                stringResource(R.string.settings_kind_units) to r.units,
                stringResource(R.string.settings_kind_nutrients) to r.nutrients,
                stringResource(R.string.settings_kind_foods) to r.foods,
                stringResource(R.string.settings_kind_entries) to r.entries,
                stringResource(R.string.settings_kind_targets) to r.targets,
            ).forEach { (label, c) -> Text(stringResource(R.string.settings_import_counts, label, c.created, c.updated, c.skipped)) }
        }
    }
    message?.let { Text(it) }
    ErrorText(error)
}

@Composable
fun SettingsScreen(nav: Navigator) {
    val app = LocalAppState.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var confirmSwitch by remember { mutableStateOf(false) }
    Screen(stringResource(R.string.settings_title), nav = nav) { modifier ->
        Column(modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionCard(stringResource(R.string.settings_mode)) {
                if (app.mode == Mode.SERVER) {
                    Text(stringResource(R.string.settings_mode_server, app.settings.serverUrl ?: "", app.settings.username ?: ""))
                } else {
                    Text(stringResource(R.string.settings_mode_local))
                }
                Muted(stringResource(R.string.settings_switch_hint))
                OutlinedButton(onClick = { confirmSwitch = true }) {
                    Text(stringResource(if (app.mode == Mode.SERVER) R.string.settings_logout else R.string.settings_switch_mode))
                }
            }
            if (app.mode == Mode.SERVER) PasswordSection()
            SectionCard(stringResource(R.string.settings_language)) {
                Muted(stringResource(R.string.settings_language_hint))
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    OutlinedButton(onClick = {
                        context.startActivity(Intent(Settings.ACTION_APP_LOCALE_SETTINGS).setData(android.net.Uri.fromParts("package", context.packageName, null)))
                    }) { Text(stringResource(R.string.settings_open_language)) }
                }
            }
            TransferSection()
            Muted(stringResource(R.string.settings_version, AppInfo.VERSION))
        }
    }
    if (confirmSwitch) {
        ConfirmDialog(
            stringResource(R.string.settings_switch_mode),
            stringResource(R.string.settings_switch_confirm),
            stringResource(R.string.action_continue),
            { scope.launch { app.leaveMode() } },
        ) { confirmSwitch = false }
    }
}
