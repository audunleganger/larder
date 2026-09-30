package com.caloriecompanion.android.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import com.caloriecompanion.shared.api.NutrientInput
import com.caloriecompanion.shared.api.UnitDto
import com.caloriecompanion.shared.api.UnitInput
import com.caloriecompanion.shared.api.UnitKind
import java.time.LocalDate

@Composable
fun kindLabel(kind: UnitKind): String = stringResource(
    when (kind) {
        UnitKind.MASS -> R.string.unit_kind_mass
        UnitKind.VOLUME -> R.string.unit_kind_volume
        UnitKind.CUSTOM -> R.string.unit_kind_custom
    },
)

private fun baseUnit(kind: UnitKind) = if (kind == UnitKind.MASS) "g" else "ml"

@Composable
fun MoreScreen(nav: Navigator) {
    Screen(stringResource(R.string.nav_more)) { modifier ->
        Column(modifier) {
            listOf(
                Dest.Units to stringResource(R.string.units_title),
                Dest.Nutrients to stringResource(R.string.nutrients_title),
                Dest.Targets to stringResource(R.string.targets_title),
                Dest.Settings to stringResource(R.string.settings_title),
            ).forEach { (dest, label) ->
                ListItem(headlineContent = { Text(label) }, modifier = Modifier.clickable { nav.push(dest) })
            }
        }
    }
}

/** Create/edit form for units (U-1, U-2). */
@Composable
private fun UnitForm(initial: UnitDto?, onSubmit: (UnitInput) -> Unit, submitLabel: String) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var kind by remember { mutableStateOf(initial?.kind ?: UnitKind.CUSTOM) }
    var factor by remember { mutableStateOf(Format.input(initial?.baseFactor)) }
    var formError by remember { mutableStateOf<String?>(null) }
    val sizeError = stringResource(R.string.units_error_size, baseUnit(kind))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Dropdown(stringResource(R.string.units_kind), UnitKind.entries.map { Choice(it, kindLabel(it)) }, kind, { kind = it })
        if (kind != UnitKind.CUSTOM) DecimalField(factor, { factor = it }, stringResource(R.string.units_size, baseUnit(kind)), Modifier.fillMaxWidth(), suffix = baseUnit(kind))
        Muted(stringResource(when (kind) { UnitKind.MASS -> R.string.units_hint_mass; UnitKind.VOLUME -> R.string.units_hint_volume; UnitKind.CUSTOM -> R.string.units_hint_custom }))
        formError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = {
            val value = Format.parseDecimal(factor)
            if (kind != UnitKind.CUSTOM && (value == null || value.isNaN() || value <= 0)) {
                formError = sizeError
            } else {
                formError = null
                onSubmit(UnitInput(name, kind, if (kind == UnitKind.CUSTOM) null else value))
            }
        }) { Text(submitLabel) }
    }
}

@Composable
fun UnitsScreen(nav: Navigator) {
    var showArchived by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    val units = rememberLoad(showArchived) { units(showArchived) }
    Screen(
        stringResource(R.string.units_title),
        nav = nav,
        actions = { FilterChip(selected = showArchived, onClick = { showArchived = !showArchived }, label = { Text(stringResource(R.string.show_archived)) }) },
        floatingActionButton = { ExtendedFloatingActionButton(onClick = { creating = true }) { Text("+ " + stringResource(R.string.units_new)) } },
    ) { modifier ->
        Column(modifier) {
            Loadable(units) { list ->
                LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                    itemsIndexed(list, key = { _, u -> u.id }) { _, unit ->
                        val size = unit.baseFactor?.takeIf { unit.kind != UnitKind.CUSTOM }?.let { "${Format.number(it, 6)} ${baseUnit(unit.kind)}" } ?: stringResource(R.string.units_per_food)
                        ListItem(
                            headlineContent = { Text(unit.name + if (unit.archived) " (${stringResource(R.string.archived)})" else "") },
                            supportingContent = { Text("${kindLabel(unit.kind)} · $size") },
                            modifier = Modifier.clickable { nav.push(Dest.UnitDetail(unit.id)) },
                        )
                    }
                }
            }
        }
    }
    if (creating) {
        var error by remember { mutableStateOf<Throwable?>(null) }
        val mutator = rememberMutator { error = it }
        AlertDialog(
            onDismissRequest = { creating = false },
            title = { Text(stringResource(R.string.units_new)) },
            text = { Column { UnitForm(null, { input -> mutator.run({ createUnit(input) }) { creating = false } }, stringResource(R.string.action_create)); ErrorText(error) } },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { creating = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
fun UnitDetailScreen(id: Long, nav: Navigator) {
    val detail = rememberLoad(id) { unitDetail(id) }
    var error by remember { mutableStateOf<Throwable?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val mutator = rememberMutator { error = it }
    val title = (detail.first as? LoadState.Loaded)?.data?.unit?.name ?: stringResource(R.string.units_title)
    Screen(title, nav = nav) { modifier ->
        Column(modifier) {
            Loadable(detail) { data ->
                Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionCard(stringResource(R.string.action_edit)) {
                        androidx.compose.runtime.key(data.unit) {
                            UnitForm(data.unit, { input -> mutator.run({ updateUnit(id, input) }) }, stringResource(R.string.action_save))
                        }
                        ErrorText(error)
                    }
                    SectionCard(stringResource(R.string.units_foods)) {
                        if (data.foods.isEmpty()) Muted(stringResource(R.string.units_no_foods))
                        data.foods.forEach { f -> Text(f.name, color = MaterialTheme.colorScheme.primary, modifier = Modifier.fillMaxWidth().clickable { nav.push(Dest.Food(f.id)) }.padding(vertical = 4.dp)) }
                        if (data.implicitFoods.isNotEmpty()) {
                            Text(stringResource(R.string.units_implicit_foods), style = MaterialTheme.typography.titleSmall)
                            data.implicitFoods.forEach { f -> Text(f.name, color = MaterialTheme.colorScheme.primary, modifier = Modifier.fillMaxWidth().clickable { nav.push(Dest.Food(f.id)) }.padding(vertical = 4.dp)) }
                        }
                    }
                    SectionCard(stringResource(R.string.units_dates)) {
                        if (data.dates.isEmpty()) Muted(stringResource(R.string.units_no_dates))
                        data.dates.forEach { d -> Text(Format.date(d), color = MaterialTheme.colorScheme.primary, modifier = Modifier.fillMaxWidth().clickable { nav.tab(Dest.Day(LocalDate.parse(d))) }.padding(vertical = 4.dp)) }
                    }
                    SectionCard(stringResource(R.string.manage)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { mutator.run({ archiveUnit(id, !data.unit.archived) }) }) { Text(stringResource(if (data.unit.archived) R.string.action_unarchive else R.string.action_archive)) }
                            OutlinedButton(onClick = { confirmDelete = true }) { Text(stringResource(R.string.action_delete)) }
                        }
                        Muted(stringResource(R.string.units_archive_hint))
                    }
                }
                if (confirmDelete) {
                    ConfirmDialog(stringResource(R.string.confirm_delete_title, data.unit.name), stringResource(R.string.confirm_delete_text), stringResource(R.string.action_delete), {
                        mutator.run({ deleteUnit(id) }) { nav.pop() }
                    }) { confirmDelete = false }
                }
            }
        }
    }
}

/** Create/edit form for nutrients (N-1, N-6). */
@Composable
private fun NutrientForm(initial: NutrientDto?, all: List<NutrientDto>, onSubmit: (NutrientInput) -> Unit, submitLabel: String) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var measure by remember { mutableStateOf(initial?.measureUnit ?: "g") }
    var precision by remember { mutableStateOf(initial?.displayPrecision ?: 1) }
    var parentId by remember { mutableStateOf(initial?.parentId) }
    val parents = all.filter { it.parentId == null && it.id != initial?.id && !it.archived }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(measure, { measure = it }, label = { Text(stringResource(R.string.nutrients_measure_unit)) }, singleLine = true, modifier = Modifier.width(120.dp))
            Dropdown(stringResource(R.string.nutrients_precision), (0..3).map { Choice(it, it.toString()) }, precision, { precision = it }, Modifier.weight(1f))
        }
        Dropdown(stringResource(R.string.nutrients_parent), listOf(Choice<Long?>(null, "—")) + parents.map { Choice<Long?>(it.id, it.name) }, parentId, { parentId = it })
        Button(onClick = { onSubmit(NutrientInput(name, measure, precision, parentId)) }) { Text(submitLabel) }
    }
}

@Composable
fun NutrientsScreen(nav: Navigator) {
    var showArchived by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Throwable?>(null) }
    val mutator = rememberMutator { error = it }
    val nutrients = rememberLoad { nutrients(includeArchived = true) }
    Screen(
        stringResource(R.string.nutrients_title),
        nav = nav,
        actions = { FilterChip(selected = showArchived, onClick = { showArchived = !showArchived }, label = { Text(stringResource(R.string.show_archived)) }) },
        floatingActionButton = { ExtendedFloatingActionButton(onClick = { creating = true }) { Text("+ " + stringResource(R.string.nutrients_new)) } },
    ) { modifier ->
        Column(modifier) {
            Muted(stringResource(R.string.nutrients_order_hint), Modifier.padding(horizontal = 16.dp))
            ErrorText(error)
            Loadable(nutrients) { all ->
                val list = if (showArchived) all else all.filter { !it.archived }
                LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                    itemsIndexed(list, key = { _, n -> n.id }) { index, n ->
                        ListItem(
                            headlineContent = { Text(n.name + if (n.archived) " (${stringResource(R.string.archived)})" else "", modifier = Modifier.padding(start = if (n.parentId != null) 16.dp else 0.dp)) },
                            supportingContent = { Text(n.measureUnit) },
                            trailingContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(enabled = index > 0, onClick = {
                                        val ids = list.map { it.id }.toMutableList()
                                        ids[index] = ids[index - 1].also { ids[index - 1] = ids[index] }
                                        mutator.run({ reorderNutrients(ids) })
                                    }) { Icon(Icons.Filled.KeyboardArrowUp, contentDescription = stringResource(R.string.nutrients_move_up, n.name)) }
                                    IconButton(enabled = index < list.lastIndex, onClick = {
                                        val ids = list.map { it.id }.toMutableList()
                                        ids[index] = ids[index + 1].also { ids[index + 1] = ids[index] }
                                        mutator.run({ reorderNutrients(ids) })
                                    }) { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = stringResource(R.string.nutrients_move_down, n.name)) }
                                }
                            },
                            modifier = Modifier.clickable { nav.push(Dest.NutrientDetail(n.id)) },
                        )
                    }
                }
                if (creating) {
                    var createError by remember { mutableStateOf<Throwable?>(null) }
                    val createMutator = rememberMutator { createError = it }
                    AlertDialog(
                        onDismissRequest = { creating = false },
                        title = { Text(stringResource(R.string.nutrients_new)) },
                        text = { Column { NutrientForm(null, all, { input -> createMutator.run({ createNutrient(input) }) { creating = false } }, stringResource(R.string.action_create)); ErrorText(createError) } },
                        confirmButton = {},
                        dismissButton = { TextButton(onClick = { creating = false }) { Text(stringResource(R.string.action_cancel)) } },
                    )
                }
            }
        }
    }
}

@Composable
fun NutrientDetailScreen(id: Long, nav: Navigator) {
    val detail = rememberLoad(id) { nutrientDetail(id) }
    val all = (rememberLoad { nutrients(includeArchived = true) }.first as? LoadState.Loaded)?.data.orEmpty()
    var error by remember { mutableStateOf<Throwable?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val mutator = rememberMutator { error = it }
    val title = (detail.first as? LoadState.Loaded)?.data?.nutrient?.name ?: stringResource(R.string.nutrients_title)
    Screen(title, nav = nav) { modifier ->
        Column(modifier) {
            Loadable(detail) { data ->
                val n = data.nutrient
                Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionCard(stringResource(R.string.action_edit)) {
                        androidx.compose.runtime.key(n) { NutrientForm(n, all, { input -> mutator.run({ updateNutrient(id, input) }) }, stringResource(R.string.action_save)) }
                        ErrorText(error)
                    }
                    SectionCard(stringResource(R.string.nutrients_foods)) {
                        if (data.foods.isEmpty()) Muted(stringResource(R.string.nutrients_no_foods))
                        data.foods.forEach { f ->
                            Row(Modifier.fillMaxWidth().clickable { nav.push(Dest.Food(f.foodId)) }.padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(f.foodName, color = MaterialTheme.colorScheme.primary)
                                Text(Format.amount(f.amount, n) + (f.refAmount?.let { " / ${Format.quantity(it)} ${f.refUnitName ?: ""}" } ?: ""))
                            }
                        }
                    }
                    SectionCard(stringResource(R.string.nutrients_entries)) {
                        if (data.entries.isEmpty()) Muted(stringResource(R.string.nutrients_no_entries))
                        data.entries.take(100).forEach { e ->
                            Row(Modifier.fillMaxWidth().clickable { nav.tab(Dest.Day(LocalDate.parse(e.date))) }.padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column(Modifier.weight(1f)) {
                                    Text(e.foodName)
                                    Muted("${Format.date(e.date)} · ${Format.quantity(e.quantity)} ${e.unitName}")
                                }
                                Text(Format.amount(e.amount, n))
                            }
                        }
                    }
                    SectionCard(stringResource(R.string.manage)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { mutator.run({ archiveNutrient(id, !n.archived) }) }) { Text(stringResource(if (n.archived) R.string.action_unarchive else R.string.action_archive)) }
                            OutlinedButton(onClick = { confirmDelete = true }) { Text(stringResource(R.string.action_delete)) }
                        }
                        Muted(stringResource(R.string.nutrients_archive_hint))
                    }
                }
                if (confirmDelete) {
                    ConfirmDialog(stringResource(R.string.confirm_delete_title, n.name), stringResource(R.string.confirm_delete_text), stringResource(R.string.action_delete), {
                        mutator.run({ deleteNutrient(id) }) { nav.pop() }
                    }) { confirmDelete = false }
                }
            }
        }
    }
}
