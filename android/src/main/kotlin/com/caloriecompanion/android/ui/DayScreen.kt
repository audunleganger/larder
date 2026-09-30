package com.caloriecompanion.android.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.DatePicker
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.caloriecompanion.android.R
import com.caloriecompanion.shared.api.DayView
import com.caloriecompanion.shared.api.EntryView
import com.caloriecompanion.shared.api.NutrientDto
import com.caloriecompanion.shared.api.NutrientTotal
import com.caloriecompanion.shared.api.TargetStatus
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerButton(date: LocalDate, onPicked: (LocalDate) -> Unit, label: String? = null) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }) { Text(label ?: Format.date(date.toString())) }
    if (open) {
        val state = rememberDatePickerState(initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onPicked(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    open = false
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.action_cancel)) } },
        ) { DatePicker(state) }
    }
}

@Composable
fun StatusLabel(status: TargetStatus) {
    val (icon, text, color) = when (status) {
        TargetStatus.BELOW -> Triple("▼", stringResource(R.string.status_below), MaterialTheme.colorScheme.onSurfaceVariant)
        TargetStatus.WITHIN -> Triple("✓", stringResource(R.string.status_within), StatusColors.good)
        TargetStatus.ABOVE -> Triple("▲", stringResource(R.string.status_above), StatusColors.serious)
        TargetStatus.NONE -> return
    }
    Text("$icon $text", color = color, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
}

fun targetText(total: NutrientTotal, nutrient: NutrientDto): String {
    val f = { v: Double -> Format.number(v, nutrient.displayPrecision) }
    val min = total.targetMin
    val max = total.targetMax
    return when {
        min != null && max != null -> "${f(min)}–${f(max)} ${nutrient.measureUnit}"
        min != null -> "≥ ${f(min)} ${nutrient.measureUnit}"
        max != null -> "≤ ${f(max)} ${nutrient.measureUnit}"
        else -> ""
    }
}

@Composable
private fun TotalRow(total: NutrientTotal, nutrient: NutrientDto, entryCount: Int, unresolvedCount: Int) {
    val noData = entryCount > 0 && total.missingCount == entryCount
    Column(Modifier.fillMaxWidth().padding(start = if (nutrient.parentId != null) 16.dp else 0.dp, top = 4.dp, bottom = 4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(nutrient.name, fontWeight = if (nutrient.parentId == null) FontWeight.SemiBold else FontWeight.Normal)
            Text(if (noData) "—" else Format.amount(total.amount, nutrient), fontWeight = FontWeight.SemiBold)
        }
        if (total.status != TargetStatus.NONE) {
            val scale = maxOf(total.amount, total.targetMax ?: 0.0, total.targetMin ?: 0.0) * 1.1
            val color = when (total.status) {
                TargetStatus.WITHIN -> StatusColors.good
                TargetStatus.ABOVE -> StatusColors.serious
                else -> MaterialTheme.colorScheme.primary
            }
            LinearProgressIndicator(
                progress = { if (scale > 0) (total.amount / scale).toFloat().coerceIn(0f, 1f) else 0f },
                color = color,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatusLabel(total.status)
                Muted(stringResource(R.string.day_target, targetText(total, nutrient)))
            }
        }
        // Unresolved entries are already explained above the totals; only mention other missing values.
        if (total.missingCount > unresolvedCount) {
            Muted(if (noData) stringResource(R.string.day_no_data) else pluralStringResource(R.plurals.day_missing_data, total.missingCount, total.missingCount))
        }
    }
}

@Composable
private fun EntryRow(entry: EntryView, nutrients: Map<Long, NutrientDto>, onClick: () -> Unit) {
    val amounts = entry.nutrients.mapNotNull { a -> nutrients[a.nutrientId]?.let { it to a.amount } }
    val primary = amounts.firstOrNull()
    val secondary = amounts.drop(1).filter { it.second != null }.joinToString("  ·  ") { (n, v) -> "${n.name} ${Format.amount(v, n)}" }
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        overlineContent = { Text(entry.time) },
        headlineContent = { Text(entry.foodName, fontWeight = FontWeight.Medium) },
        supportingContent = {
            Column {
                Text("${Format.quantity(entry.quantity)} ${entry.unitName}" + (entry.note?.let { " · $it" } ?: ""))
                if (entry.unresolved) {
                    Text("⚠ " + stringResource(R.string.day_incomplete), color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.labelMedium)
                } else if (secondary.isNotEmpty()) {
                    Muted(secondary)
                }
            }
        },
        trailingContent = { primary?.let { (n, v) -> Text(Format.amount(v, n), fontWeight = FontWeight.SemiBold) } },
    )
}

/** The day view (D-1, D-2, E-3, T-2). */
@Composable
fun DayScreen(date: LocalDate, nav: Navigator) {
    val day = rememberLoad(date) { day(date.toString()) }
    val nutrients = rememberLoad { nutrients(includeArchived = true) }
    val byId = (nutrients.first as? LoadState.Loaded)?.data?.associateBy { it.id }.orEmpty()
    val today = LocalDate.now()

    Screen(
        title = if (date == today) stringResource(R.string.day_today) else Format.dayTitle(date),
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { nav.push(Dest.EntryEdit(date, null)) }) { Text("+ " + stringResource(R.string.entry_add_title)) }
        },
    ) { modifier ->
        LazyColumn(modifier, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = { nav.tab(Dest.Day(date.minusDays(1))) }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.day_previous))
                    }
                    DatePickerButton(date, onPicked = { nav.tab(Dest.Day(it)) })
                    IconButton(onClick = { nav.tab(Dest.Day(date.plusDays(1))) }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.day_next))
                    }
                    if (date != today) TextButton(onClick = { nav.tab(Dest.Day(today)) }) { Text(stringResource(R.string.day_today)) }
                }
            }
            val entries = (day.first as? LoadState.Loaded)?.data?.entries
            if (entries != null) {
                item { Text(stringResource(R.string.day_entries), style = MaterialTheme.typography.titleMedium) }
                if (entries.isEmpty()) {
                    item { Muted(stringResource(R.string.day_no_entries)) }
                }
                items(entries, key = { it.id }) { entry ->
                    EntryRow(entry, byId) { nav.push(Dest.EntryEdit(date, entry.id)) }
                    HorizontalDivider(color = Color.Transparent)
                }
            }
            item {
                Loadable(day) { data: DayView ->
                    SectionCard(stringResource(R.string.day_totals)) {
                        val unresolved = data.entries.count { it.unresolved }
                        if (unresolved > 0) {
                            Text("⚠ " + pluralStringResource(R.plurals.day_unresolved, unresolved, unresolved), color = MaterialTheme.colorScheme.tertiary)
                        }
                        data.totals.forEach { total -> byId[total.nutrientId]?.let { TotalRow(total, it, data.entries.size, unresolved) } }
                        TextButton(onClick = { nav.tab(Dest.Targets) }) { Text(stringResource(R.string.day_edit_targets)) }
                    }
                }
            }
        }
    }
}
