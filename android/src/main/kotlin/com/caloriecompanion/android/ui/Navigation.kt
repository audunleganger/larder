package com.caloriecompanion.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.caloriecompanion.android.R
import java.time.LocalDate

sealed interface Dest {
    data class Day(val date: LocalDate) : Dest
    data class EntryEdit(val date: LocalDate, val entryId: Long?) : Dest
    data object Foods : Dest
    data class Food(val id: Long) : Dest
    data object History : Dest
    data object More : Dest
    data object Units : Dest
    data class UnitDetail(val id: Long) : Dest
    data object Nutrients : Dest
    data class NutrientDetail(val id: Long) : Dest
    data object Targets : Dest
    data object Settings : Dest
}

/** A minimal back stack; tabs reset it to their root. */
class Navigator(private val stack: SnapshotStateList<Dest>) {
    val current: Dest get() = stack.last()
    val root: Dest get() = stack.first()
    val canGoBack: Boolean get() = stack.size > 1

    fun push(dest: Dest) {
        stack.add(dest)
    }

    fun pop() {
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
    }

    fun tab(dest: Dest) {
        stack.clear()
        stack.add(dest)
    }
}

@Composable
fun MainScreen() {
    val stack = remember { mutableStateListOf<Dest>(Dest.Day(LocalDate.now())) }
    val nav = remember { Navigator(stack) }
    BackHandler(enabled = nav.canGoBack) { nav.pop() }

    val tabs = listOf(
        Dest.Day(LocalDate.now()) to stringResource(R.string.nav_day),
        Dest.Foods to stringResource(R.string.nav_foods),
        Dest.History to stringResource(R.string.nav_history),
        Dest.More to stringResource(R.string.nav_more),
    )
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            NavigationBar {
                tabs.forEach { (dest, label) ->
                    val selected = when (dest) {
                        is Dest.Day -> nav.root is Dest.Day
                        Dest.More -> nav.root in setOf(Dest.More, Dest.Units, Dest.Nutrients, Dest.Targets, Dest.Settings)
                        else -> nav.root == dest
                    }
                    NavigationBarItem(selected = selected, onClick = { nav.tab(dest) }, icon = { Text(tabIcon(dest)) }, label = { Text(label) })
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (val dest = nav.current) {
                is Dest.Day -> DayScreen(dest.date, nav)
                is Dest.EntryEdit -> EntryEditorScreen(dest.date, dest.entryId, nav)
                Dest.Foods -> FoodsScreen(nav)
                is Dest.Food -> FoodEditorScreen(dest.id, nav)
                Dest.History -> HistoryScreen()
                Dest.More -> MoreScreen(nav)
                Dest.Units -> UnitsScreen(nav)
                is Dest.UnitDetail -> UnitDetailScreen(dest.id, nav)
                Dest.Nutrients -> NutrientsScreen(nav)
                is Dest.NutrientDetail -> NutrientDetailScreen(dest.id, nav)
                Dest.Targets -> TargetsScreen(nav)
                Dest.Settings -> SettingsScreen(nav)
            }
        }
    }
}

private fun tabIcon(dest: Dest): String = when (dest) {
    is Dest.Day -> "▦"
    Dest.Foods -> "◉"
    Dest.History -> "▮▯"
    else -> "☰"
}

/** Screen chrome: a top bar with optional back button and actions. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Screen(
    title: String,
    nav: Navigator? = null,
    actions: @Composable RowScope.() -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (Modifier) -> Unit,
) {
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    if (nav != null && nav.canGoBack) {
                        TextButton(onClick = { nav.pop() }) { Text("‹ " + stringResource(R.string.action_back)) }
                    }
                },
                actions = actions,
            )
        },
        floatingActionButton = floatingActionButton,
    ) { padding -> content(Modifier.padding(padding)) }
}
