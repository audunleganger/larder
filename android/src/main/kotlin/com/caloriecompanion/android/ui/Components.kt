package com.caloriecompanion.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.caloriecompanion.android.AppState
import com.caloriecompanion.android.R
import com.caloriecompanion.android.data.Repository
import com.caloriecompanion.android.data.RepositoryException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

val LocalAppState = staticCompositionLocalOf<AppState> { error("No AppState") }

@Composable
fun repository(): Repository = LocalAppState.current.repository ?: error("No repository")

sealed interface LoadState<out T> {
    data object Loading : LoadState<Nothing>
    data class Loaded<T>(val data: T) : LoadState<T>
    data class Failed(val error: Throwable) : LoadState<Nothing>
}

/**
 * Loads data, reloading when [keys] change or any data changes (AppState.dataVersion).
 * Keeps showing the previous data while reloading, to avoid flicker.
 */
@Composable
fun <T> rememberLoad(vararg keys: Any?, load: suspend Repository.() -> T): Pair<LoadState<T>, () -> Unit> {
    val app = LocalAppState.current
    val repo = app.repository
    var state by remember { mutableStateOf<LoadState<T>>(LoadState.Loading) }
    var retry by remember { mutableStateOf(0) }
    LaunchedEffect(*keys, app.dataVersion, retry, repo) {
        if (repo == null) return@LaunchedEffect
        state = try {
            LoadState.Loaded(repo.load())
        } catch (e: RepositoryException) {
            LoadState.Failed(e)
        }
    }
    return state to { retry++ }
}

/** Shows loading/error states and the content once loaded. */
@Composable
fun <T> Loadable(state: Pair<LoadState<T>, () -> Unit>, content: @Composable (T) -> Unit) {
    when (val s = state.first) {
        LoadState.Loading -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        is LoadState.Failed -> Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ErrorText(s.error)
            OutlinedButton(onClick = state.second) { Text(stringResource(R.string.action_retry)) }
        }
        is LoadState.Loaded -> content(s.data)
    }
}

/** Runs a change in the background, reports errors, and refreshes all screens on success. */
class Mutator(private val scope: CoroutineScope, private val app: AppState, private val onError: (Throwable?) -> Unit) {
    fun run(block: suspend Repository.() -> Unit, onSuccess: () -> Unit = {}) {
        val repo = app.repository ?: return
        onError(null)
        scope.launch {
            try {
                repo.block()
                app.changed()
                onSuccess()
            } catch (e: RepositoryException) {
                onError(e)
            }
        }
    }
}

@Composable
fun rememberMutator(onError: (Throwable?) -> Unit): Mutator {
    val scope = rememberCoroutineScope()
    val app = LocalAppState.current
    return remember(scope, app) { Mutator(scope, app, onError) }
}

@Composable
fun errorMessage(error: Throwable): String {
    if (error !is RepositoryException) return error.message ?: error.toString()
    return when (error.code) {
        RepositoryException.NETWORK -> stringResource(R.string.error_network)
        AppState.INCOMPATIBLE -> stringResource(R.string.error_incompatible)
        "NAME_TAKEN" -> stringResource(R.string.error_name_taken)
        "NAME_REQUIRED" -> stringResource(R.string.error_name_required)
        "NOT_FOUND" -> stringResource(R.string.error_not_found)
        "INVALID_CREDENTIALS" -> stringResource(R.string.error_invalid_credentials)
        "WEAK_PASSWORD" -> stringResource(R.string.error_weak_password)
        "UNAUTHORIZED" -> stringResource(R.string.error_unauthorized)
        "REFERENCED" -> {
            val parts = error.details.filterValues { it > 0 }.map { (what, count) ->
                when (what) {
                    "entries" -> pluralStringResource(R.plurals.ref_entries, count.toInt(), count.toInt())
                    "foods" -> pluralStringResource(R.plurals.ref_foods, count.toInt(), count.toInt())
                    "targets" -> pluralStringResource(R.plurals.ref_targets, count.toInt(), count.toInt())
                    else -> "$count $what"
                }
            }
            stringResource(R.string.error_referenced, parts.joinToString(", "))
        }
        // Validation and import messages come from the shared code in English.
        else -> error.message ?: error.code
    }
}

@Composable
fun ErrorText(error: Throwable?) {
    if (error == null) return
    Text(errorMessage(error), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
}

@Composable
fun SectionCard(title: String? = null, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (title != null) Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

/** Decimal input accepting "," and "." (L-2); marked as error when unparsable or negative. */
@Composable
fun DecimalField(value: String, onValueChange: (String) -> Unit, label: String, modifier: Modifier = Modifier, suffix: String? = null) {
    val parsed = Format.parseDecimal(value)
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        isError = parsed != null && (parsed.isNaN() || parsed < 0),
        suffix = suffix?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
    )
}

data class Choice<T>(val value: T, val label: String, val group: String? = null)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> Dropdown(label: String, choices: List<Choice<T>>, selected: T?, onSelected: (T) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { if (enabled) expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = choices.firstOrNull { it.value == selected }?.label ?: "",
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(label) },
            singleLine = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            var group: String? = null
            choices.forEach { choice ->
                if (choice.group != null && choice.group != group) {
                    group = choice.group
                    Text(choice.group, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                }
                DropdownMenuItem(text = { Text(choice.label) }, onClick = {
                    onSelected(choice.value)
                    expanded = false
                })
            }
        }
    }
}

@Composable
fun ConfirmDialog(title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = { onDismiss(); onConfirm() }) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
fun Muted(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
}
