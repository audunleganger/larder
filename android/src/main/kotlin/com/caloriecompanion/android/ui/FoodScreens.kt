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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.caloriecompanion.android.R
import com.caloriecompanion.shared.api.FoodDetail
import com.caloriecompanion.shared.api.FoodInput
import com.caloriecompanion.shared.api.FoodNutrientValue
import com.caloriecompanion.shared.api.FoodUnitLink
import com.caloriecompanion.shared.api.NutrientDto
import com.caloriecompanion.shared.api.UnitDto

/** Food list with search and quick create (F-1). */
@Composable
fun FoodsScreen(nav: Navigator) {
    var query by remember { mutableStateOf("") }
    var showArchived by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    val foods = rememberLoad(query, showArchived) { foods(query, showArchived) }
    val units = (rememberLoad { units(includeArchived = true) }.first as? LoadState.Loaded)?.data.orEmpty()

    Screen(
        title = stringResource(R.string.foods_title),
        floatingActionButton = { ExtendedFloatingActionButton(onClick = { creating = true }) { Text("+ " + stringResource(R.string.foods_new)) } },
    ) { modifier ->
        Column(modifier) {
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(query, { query = it }, label = { Text(stringResource(R.string.food_search)) }, singleLine = true, modifier = Modifier.weight(1f))
                FilterChip(selected = showArchived, onClick = { showArchived = !showArchived }, label = { Text(stringResource(R.string.show_archived)) })
            }
            Loadable(foods) { list ->
                if (list.isEmpty()) Muted(stringResource(R.string.foods_empty), Modifier.padding(16.dp))
                LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                    items(list, key = { it.id }) { food ->
                        val reference = if (food.refAmount != null) {
                            stringResource(R.string.foods_per, Format.quantity(food.refAmount!!), units.firstOrNull { it.id == food.refUnitId }?.name ?: "")
                        } else {
                            stringResource(R.string.foods_no_reference)
                        }
                        ListItem(
                            headlineContent = { Text(food.name + if (food.archived) " (${stringResource(R.string.archived)})" else "") },
                            supportingContent = { Text(reference) },
                            modifier = Modifier.clickable { nav.push(Dest.Food(food.id)) },
                        )
                    }
                }
            }
        }
    }
    if (creating) {
        NameDialog(stringResource(R.string.foods_new), onDismiss = { creating = false }) { name, mutator ->
            mutator.run({ val food = createFood(FoodInput(name)); nav.push(Dest.Food(food.id)) }) { creating = false }
        }
    }
}

/** Asks for a name, then runs [onSubmit]; shows errors such as NAME_TAKEN inline. */
@Composable
fun NameDialog(title: String, onDismiss: () -> Unit, onSubmit: (String, Mutator) -> Unit) {
    var name by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<Throwable?>(null) }
    val mutator = rememberMutator { error = it }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.name)) }, singleLine = true)
                Muted(stringResource(R.string.foods_new_hint))
                ErrorText(error)
            }
        },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { onSubmit(name, mutator) }) { Text(stringResource(R.string.action_create)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

private class LinkRow(val key: Int, unitId: Long?, amount: String, equalsUnitId: Long?) {
    var unitId by mutableStateOf(unitId)
    var amount by mutableStateOf(amount)
    var equalsUnitId by mutableStateOf(equalsUnitId)
}

@Composable
private fun FoodForm(detail: FoodDetail, units: List<UnitDto>, nutrients: List<NutrientDto>, nav: Navigator) {
    val food = detail.food
    var name by remember { mutableStateOf(food.name) }
    var refAmount by remember { mutableStateOf(Format.input(food.refAmount)) }
    var refUnitId by remember { mutableStateOf(food.refUnitId) }
    var notes by remember { mutableStateOf(food.notes ?: "") }
    val values = remember { mutableStateMapOf<Long, String>().apply { food.nutrients.forEach { put(it.nutrientId, Format.input(it.amount)) } } }
    var nextKey by remember { mutableStateOf(food.units.size) }
    val links = remember { mutableStateListOf<LinkRow>().apply { food.units.forEachIndexed { i, l -> add(LinkRow(i, l.unitId, Format.input(l.equalsAmount), l.equalsUnitId)) } } }
    var error by remember { mutableStateOf<Throwable?>(null) }
    var formError by remember { mutableStateOf<String?>(null) }
    var saved by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val mutator = rememberMutator { error = it }
    val unitChoices = units.map { Choice(it.id, it.name + if (it.archived) " (${stringResource(R.string.archived)})" else "") }
    val notSet = stringResource(R.string.foods_not_set)
    val errRef = stringResource(R.string.foods_error_ref)
    val errNumber = stringResource(R.string.foods_error_number)

    fun build(): FoodInput? {
        val amount = Format.parseDecimal(refAmount)
        if (amount != null && (amount.isNaN() || amount <= 0) || (amount == null) != (refUnitId == null)) {
            formError = errRef
            return null
        }
        val nutrientValues = values.mapNotNull { (id, text) ->
            val v = Format.parseDecimal(text) ?: return@mapNotNull null
            if (v.isNaN() || v < 0) { formError = errNumber; return null }
            FoodNutrientValue(id, v)
        }
        val unitLinks = links.mapNotNull { row ->
            val unit = row.unitId ?: return@mapNotNull null
            val equals = Format.parseDecimal(row.amount)
            if (equals != null && (equals.isNaN() || equals <= 0)) { formError = errNumber; return null }
            val sized = equals != null && row.equalsUnitId != null
            FoodUnitLink(unit, if (sized) equals else null, if (sized) row.equalsUnitId else null)
        }
        return FoodInput(name, amount, refUnitId, notes.trim().ifEmpty { null }, nutrientValues, unitLinks)
    }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionCard(stringResource(R.string.foods_details)) {
            OutlinedTextField(name, { name = it; saved = false }, label = { Text(stringResource(R.string.name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DecimalField(refAmount, { refAmount = it; saved = false }, stringResource(R.string.foods_ref_amount), Modifier.width(130.dp))
                Dropdown(stringResource(R.string.foods_ref_unit), listOf(Choice<Long?>(null, notSet)) + unitChoices.map { Choice<Long?>(it.value, it.label) }, refUnitId, { refUnitId = it; saved = false }, Modifier.weight(1f))
            }
            Muted(stringResource(R.string.foods_ref_hint))
            OutlinedTextField(notes, { notes = it; saved = false }, label = { Text(stringResource(R.string.foods_notes)) }, modifier = Modifier.fillMaxWidth())
        }
        val refUnitName = units.firstOrNull { it.id == refUnitId }?.name
        SectionCard(if (refUnitName != null && refAmount.isNotBlank()) stringResource(R.string.foods_nutrients_per, refAmount, refUnitName) else stringResource(R.string.foods_nutrients)) {
            nutrients.filter { !it.archived || values.containsKey(it.id) }.forEach { n ->
                DecimalField(
                    values[n.id] ?: "",
                    { values[n.id] = it; saved = false },
                    n.name,
                    Modifier.fillMaxWidth().padding(start = if (n.parentId != null) 16.dp else 0.dp),
                    suffix = n.measureUnit,
                )
            }
        }
        SectionCard(stringResource(R.string.foods_units)) {
            Muted(stringResource(R.string.foods_units_hint))
            links.forEach { row ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("1")
                        Dropdown(stringResource(R.string.foods_unit), unitChoices, row.unitId, { row.unitId = it; saved = false }, Modifier.weight(1f))
                        TextButton(onClick = { links.remove(row); saved = false }) { Text("✕") }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("=")
                        DecimalField(row.amount, { row.amount = it; saved = false }, stringResource(R.string.foods_equals_amount), Modifier.width(110.dp))
                        Dropdown(stringResource(R.string.foods_equals_unit), unitChoices.filter { it.value != row.unitId }, row.equalsUnitId, { row.equalsUnitId = it; saved = false }, Modifier.weight(1f))
                    }
                }
            }
            OutlinedButton(onClick = { links.add(LinkRow(nextKey++, null, "", refUnitId)) }) { Text(stringResource(R.string.foods_add_unit)) }
            Text(stringResource(R.string.foods_usable_units), style = MaterialTheme.typography.titleSmall)
            val ref = units.firstOrNull { it.id == food.refUnitId }?.name ?: ""
            if (detail.usableUnits.isEmpty()) Muted(stringResource(R.string.foods_no_usable_units))
            detail.usableUnits.forEach { u ->
                val size = u.amountInRefUnit?.let { "= ${Format.number(it, 3)} $ref" } ?: ("⚠ " + stringResource(R.string.foods_no_size))
                val auto = if (!u.explicit) " · " + stringResource(R.string.foods_automatic) else ""
                Text("${u.name} $size$auto", style = MaterialTheme.typography.bodyMedium)
            }
        }
        formError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        ErrorText(error)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = {
                formError = null
                build()?.let { input -> mutator.run({ updateFood(food.id, input) }) { saved = true } }
            }) { Text(stringResource(R.string.action_save)) }
            if (saved) Text("✓ " + stringResource(R.string.saved), color = StatusColors.good)
        }
        SectionCard(stringResource(R.string.foods_entries)) {
            if (detail.entries.isEmpty()) Muted(stringResource(R.string.foods_no_entries))
            detail.entries.groupBy { it.date }.forEach { (date, entries) ->
                Row(Modifier.fillMaxWidth().clickable { nav.tab(Dest.Day(java.time.LocalDate.parse(date))) }.padding(vertical = 4.dp)) {
                    Text(Format.date(date), Modifier.width(120.dp), color = MaterialTheme.colorScheme.primary)
                    Text(entries.joinToString(", ") { "${Format.quantity(it.quantity)} ${it.unitName}" })
                }
            }
        }
        SectionCard(stringResource(R.string.manage)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { mutator.run({ archiveFood(food.id, !food.archived) }) }) {
                    Text(stringResource(if (food.archived) R.string.action_unarchive else R.string.action_archive))
                }
                OutlinedButton(onClick = { confirmDelete = true }) { Text(stringResource(R.string.action_delete)) }
            }
            Muted(stringResource(R.string.foods_archive_hint))
        }
    }
    if (confirmDelete) {
        ConfirmDialog(stringResource(R.string.confirm_delete_title, food.name), stringResource(R.string.confirm_delete_text), stringResource(R.string.action_delete), {
            mutator.run({ deleteFood(food.id) }) { nav.pop() }
        }) { confirmDelete = false }
    }
}

/** Food detail and editor (F-3 … F-9). */
@Composable
fun FoodEditorScreen(id: Long, nav: Navigator) {
    val detail = rememberLoad(id) { foodDetail(id) }
    val units = rememberLoad { units(includeArchived = true) }
    val nutrients = rememberLoad { nutrients(includeArchived = true) }
    val title = (detail.first as? LoadState.Loaded)?.data?.food?.name ?: stringResource(R.string.foods_title)
    Screen(title = title, nav = nav) { modifier ->
        Column(modifier) {
            Loadable(detail) { data ->
                val u = (units.first as? LoadState.Loaded)?.data
                val n = (nutrients.first as? LoadState.Loaded)?.data
                if (u != null && n != null) {
                    // Re-create the form when the saved food changes, so it shows what was stored.
                    androidx.compose.runtime.key(data.food) { FoodForm(data, u, n, nav) }
                }
            }
        }
    }
}
