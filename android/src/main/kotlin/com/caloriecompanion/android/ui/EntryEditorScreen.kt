package com.caloriecompanion.android.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.caloriecompanion.android.R
import com.caloriecompanion.android.data.RepositoryException
import com.caloriecompanion.shared.api.EntryInput
import com.caloriecompanion.shared.api.FoodDetail
import com.caloriecompanion.shared.api.FoodInput
import com.caloriecompanion.shared.api.FoodSummary
import com.caloriecompanion.shared.api.PreviewInput
import com.caloriecompanion.shared.api.PreviewResult
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private data class PickedFood(val id: Long, val name: String)

/** Unit to preselect: the last one used for the food, else the first resolvable explicit unit. */
private fun defaultUnit(detail: FoodDetail?): Long? {
    if (detail == null) return null
    detail.entries.firstOrNull()?.let { return it.unitId }
    val usable = detail.usableUnits
    return (usable.firstOrNull { it.explicit && it.amountInRefUnit != null } ?: usable.firstOrNull { it.amountInRefUnit != null } ?: usable.firstOrNull())?.unitId
}

/** Registers or edits an entry (E-1, E-5) with a live preview (E-2). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EntryEditorScreen(initialDate: LocalDate, entryId: Long?, nav: Navigator) {
    val repo = repository()
    var loaded by remember { mutableStateOf(entryId == null) }
    var food by remember { mutableStateOf<PickedFood?>(null) }
    var unitId by remember { mutableStateOf<Long?>(null) }
    var quantity by remember { mutableStateOf("1") }
    var date by remember { mutableStateOf(initialDate) }
    var time by remember { mutableStateOf(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))) }
    var note by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<FoodSummary>>(emptyList()) }
    var preview by remember { mutableStateOf<PreviewResult?>(null) }
    var error by remember { mutableStateOf<Throwable?>(null) }
    var formError by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val mutator = rememberMutator { error = it }

    LaunchedEffect(entryId) {
        if (entryId == null) return@LaunchedEffect
        try {
            val entry = repo.entry(entryId)
            food = PickedFood(entry.foodId, entry.foodName)
            unitId = entry.unitId
            quantity = Format.input(entry.quantity)
            date = LocalDate.parse(entry.date)
            time = entry.time
            note = entry.note ?: ""
            loaded = true
        } catch (e: RepositoryException) {
            error = e
        }
    }
    LaunchedEffect(query, food) {
        if (food != null) return@LaunchedEffect
        delay(150)
        results = try { repo.foods(query).take(10) } catch (e: RepositoryException) { error = e; emptyList() }
    }

    val detailState = rememberLoad(food?.id) { food?.let { foodDetail(it.id) } }
    val detail = (detailState.first as? LoadState.Loaded)?.data
    val allUnits = (rememberLoad { units() }.first as? LoadState.Loaded)?.data.orEmpty()
    val nutrients = (rememberLoad { nutrients() }.first as? LoadState.Loaded)?.data.orEmpty()
    val effectiveUnit = unitId ?: defaultUnit(detail)
    val qty = Format.parseDecimal(quantity)?.takeIf { !it.isNaN() && it > 0 }

    LaunchedEffect(food?.id, effectiveUnit, qty) {
        preview = null
        val f = food ?: return@LaunchedEffect
        val u = effectiveUnit ?: return@LaunchedEffect
        val q = qty ?: return@LaunchedEffect
        delay(250)
        preview = try { repo.preview(PreviewInput(f.id, u, q)) } catch (e: RepositoryException) { null }
    }

    val errFood = stringResource(R.string.entry_error_food)
    val errUnit = stringResource(R.string.entry_error_unit)
    val errQty = stringResource(R.string.entry_error_quantity)
    val errTime = stringResource(R.string.entry_error_time)

    Screen(
        title = stringResource(if (entryId == null) R.string.entry_add_title else R.string.entry_edit_title),
        nav = nav,
        actions = {
            if (entryId != null) TextButton(onClick = { confirmDelete = true }) { Text(stringResource(R.string.action_delete)) }
        },
    ) { modifier ->
        if (!loaded) {
            Column(modifier.padding(16.dp)) { ErrorText(error) }
            return@Screen
        }
        Column(modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val picked = food
            if (picked == null) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(stringResource(R.string.entry_food)) },
                    placeholder = { Text(stringResource(R.string.food_search)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                results.forEach { f ->
                    ListItem(headlineContent = { Text(f.name) }, modifier = Modifier.clickable {
                        food = PickedFood(f.id, f.name)
                        unitId = null
                    })
                }
                val name = query.trim()
                if (name.isNotEmpty() && results.none { it.name.equals(name, ignoreCase = true) }) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.food_create_named, name), color = MaterialTheme.colorScheme.primary) },
                        modifier = Modifier.clickable {
                            mutator.run({
                                val created = createFood(FoodInput(name))
                                food = PickedFood(created.id, created.name)
                                unitId = null
                            })
                        },
                    )
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Muted(stringResource(R.string.entry_food))
                        Text(picked.name, style = MaterialTheme.typography.titleMedium)
                    }
                    TextButton(onClick = { nav.push(Dest.Food(picked.id)) }) { Text(stringResource(R.string.entry_open_food)) }
                    TextButton(onClick = { food = null; query = "" }) { Text(stringResource(R.string.action_change)) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DecimalField(quantity, { quantity = it }, stringResource(R.string.entry_quantity), Modifier.width(120.dp))
                    val usable = detail?.usableUnits.orEmpty()
                    val usableIds = usable.map { it.unitId }.toSet()
                    val forFood = stringResource(R.string.entry_units_for_food)
                    val other = stringResource(R.string.entry_other_units)
                    val choices = usable.map { Choice(it.unitId, it.name + if (it.amountInRefUnit == null) " ⚠" else "", forFood) } +
                        allUnits.filter { it.id !in usableIds }.map { Choice(it.id, it.name, other) }
                    Dropdown(stringResource(R.string.entry_unit), choices, effectiveUnit, { unitId = it }, Modifier.weight(1f))
                }
                val p = preview
                if (p != null && qty != null) {
                    if (p.unresolved) {
                        Text("⚠ " + stringResource(R.string.entry_unresolved), color = MaterialTheme.colorScheme.tertiary)
                    } else {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            p.nutrients.take(6).forEach { amount ->
                                nutrients.firstOrNull { it.id == amount.nutrientId }?.let { n ->
                                    AssistChip(onClick = {}, label = { Text("${n.name} ${Format.amount(amount.amount, n)}") })
                                }
                            }
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                DatePickerButton(date, onPicked = { date = it })
                OutlinedTextField(value = time, onValueChange = { time = it }, label = { Text(stringResource(R.string.entry_time)) }, singleLine = true, modifier = Modifier.width(110.dp), isError = Format.parseTime(time) == null)
            }
            OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text(stringResource(R.string.entry_note)) }, modifier = Modifier.fillMaxWidth())
            formError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            ErrorText(error)
            Button(onClick = {
                formError = null
                val f = food ?: return@Button run { formError = errFood }
                val u = effectiveUnit ?: return@Button run { formError = errUnit }
                val q = qty ?: return@Button run { formError = errQty }
                val t = Format.parseTime(time) ?: return@Button run { formError = errTime }
                val input = EntryInput(f.id, u, q, date.toString(), t, note.trim().ifEmpty { null })
                mutator.run({ if (entryId == null) createEntry(input) else updateEntry(entryId, input) }) {
                    nav.pop()
                    if (entryId == null && date != initialDate) nav.tab(Dest.Day(date))
                }
            }) { Text(stringResource(if (entryId == null) R.string.entry_add else R.string.action_save)) }
        }
    }
    if (confirmDelete && entryId != null) {
        ConfirmDialog(stringResource(R.string.entry_delete_title), stringResource(R.string.entry_delete_text), stringResource(R.string.action_delete), {
            mutator.run({ deleteEntry(entryId) }) { nav.pop() }
        }) { confirmDelete = false }
    }
}
