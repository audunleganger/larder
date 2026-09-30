package com.caloriecompanion.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.caloriecompanion.android.R
import com.caloriecompanion.shared.api.NutrientDto
import com.caloriecompanion.shared.api.TargetDto
import com.caloriecompanion.shared.api.TargetInput
import java.time.LocalDate

private fun rangeText(t: TargetDto?, n: NutrientDto, none: String): String {
    if (t == null) return none
    val f = { v: Double -> Format.number(v, n.displayPrecision) }
    return when {
        t.min != null && t.max != null -> "${f(t.min!!)}–${f(t.max!!)} ${n.measureUnit}"
        t.min != null -> "≥ ${f(t.min!!)} ${n.measureUnit}"
        t.max != null -> "≤ ${f(t.max!!)} ${n.measureUnit}"
        else -> none
    }
}

@Composable
private fun TargetCard(nutrient: NutrientDto, current: TargetDto?) {
    var min by remember { mutableStateOf(Format.input(current?.min)) }
    var max by remember { mutableStateOf(Format.input(current?.max)) }
    var from by remember { mutableStateOf(LocalDate.now()) }
    var error by remember { mutableStateOf<Throwable?>(null) }
    var formError by remember { mutableStateOf<String?>(null) }
    val mutator = rememberMutator { error = it }
    val errNumber = stringResource(R.string.targets_error_number)
    val errOrder = stringResource(R.string.targets_error_order)
    SectionCard(nutrient.name) {
        Muted(rangeText(current, nutrient, stringResource(R.string.targets_none)))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DecimalField(min, { min = it }, stringResource(R.string.targets_min), Modifier.weight(1f), suffix = nutrient.measureUnit)
            DecimalField(max, { max = it }, stringResource(R.string.targets_max), Modifier.weight(1f), suffix = nutrient.measureUnit)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.targets_from))
            DatePickerButton(from, onPicked = { from = it })
            Button(onClick = {
                formError = null
                val lo = Format.parseDecimal(min)
                val hi = Format.parseDecimal(max)
                when {
                    listOf(lo, hi).any { it != null && (it.isNaN() || it < 0) } -> formError = errNumber
                    lo != null && hi != null && lo > hi -> formError = errOrder
                    else -> mutator.run({ setTarget(TargetInput(nutrient.id, lo, hi, from.toString())) })
                }
            }) { Text(stringResource(R.string.action_save)) }
        }
        formError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        ErrorText(error)
    }
}

/** Daily targets (T-1, T-3). */
@Composable
fun TargetsScreen(nav: Navigator) {
    val nutrients = rememberLoad { nutrients() }
    val targets = rememberLoad { targets() }
    var error by remember { mutableStateOf<Throwable?>(null) }
    val mutator = rememberMutator { error = it }
    val today = LocalDate.now().toString()
    Screen(stringResource(R.string.targets_title), nav = nav) { modifier ->
        Column(modifier) {
            Loadable(targets) { list ->
                Loadable(nutrients) { ns ->
                    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Muted(stringResource(R.string.targets_hint))
                        ns.forEach { n ->
                            val current = list.filter { it.nutrientId == n.id && it.effectiveFrom <= today }.maxByOrNull { it.effectiveFrom }
                            androidx.compose.runtime.key(n.id, current) { TargetCard(n, current) }
                        }
                        SectionCard(stringResource(R.string.targets_history)) {
                            if (list.isEmpty()) Muted(stringResource(R.string.targets_no_history))
                            ErrorText(error)
                            list.sortedByDescending { it.effectiveFrom }.forEach { t ->
                                val n = ns.firstOrNull { it.id == t.nutrientId } ?: return@forEach
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text("${n.name}: ${rangeText(t, n, stringResource(R.string.targets_cleared))}")
                                        Muted(stringResource(R.string.targets_from_date, Format.date(t.effectiveFrom)))
                                    }
                                    TextButton(onClick = { mutator.run({ deleteTarget(t.id) }) }) { Text(stringResource(R.string.action_delete)) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
