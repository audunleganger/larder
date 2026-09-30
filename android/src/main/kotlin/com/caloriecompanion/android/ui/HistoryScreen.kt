package com.caloriecompanion.android.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.caloriecompanion.android.R
import com.caloriecompanion.shared.api.HistoryView
import com.caloriecompanion.shared.api.NutrientDto
import java.time.LocalDate

private data class Point(val amount: Double?, val average: Double?, val min: Double?, val max: Double?)

/** Daily values (null = not logged or no data, H-4) with a 7-day rolling average. */
private fun series(view: HistoryView, nutrientId: Long): List<Point> {
    val values = view.days.map { day ->
        val total = day.totals.firstOrNull { it.nutrientId == nutrientId }
        if (day.entryCount > 0 && total != null && total.missingCount < day.entryCount) total.amount else null
    }
    return view.days.mapIndexed { i, day ->
        val window = values.subList(maxOf(0, i - 6), i + 1).filterNotNull()
        val total = day.totals.firstOrNull { it.nutrientId == nutrientId }
        Point(values[i], window.takeIf { it.isNotEmpty() }?.average(), total?.targetMin, total?.targetMax)
    }
}

@Composable
private fun Chart(points: List<Point>, showAverage: Boolean, description: String) {
    val dark = isSystemInDarkTheme()
    val barColor = if (dark) StatusColors.series1Dark else StatusColors.series1Light
    val lineColor = if (dark) StatusColors.series2Dark else StatusColors.series2Light
    val targetColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val top = points.maxOfOrNull { maxOf(it.amount ?: 0.0, it.max ?: 0.0, it.min ?: 0.0) }?.takeIf { it > 0 } ?: 1.0
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = MaterialTheme.typography.labelSmall.fontSize)
    Canvas(Modifier.fillMaxWidth().height(220.dp).semantics { contentDescription = description }) {
        // Left margin for the value labels; the plot area is to the right of it.
        val pad = 44.dp.toPx()
        val plotWidth = size.width - pad
        val scaleY = size.height / (top * 1.1).toFloat()
        val slot = plotWidth / points.size
        val barWidth = (slot * 0.7f).coerceAtMost(28.dp.toPx())
        for (i in 0..3) {
            val y = size.height - size.height * i / 4
            drawLine(gridColor, Offset(pad, y), Offset(size.width, y), 1f)
            val label = measurer.measure(Format.number(top * 1.1 * i / 4, 0), labelStyle)
            drawText(label, topLeft = Offset(pad - label.size.width - 4.dp.toPx(), (y - label.size.height / 2).coerceIn(0f, size.height - label.size.height)))
        }
        points.forEachIndexed { i, p ->
            val cx = pad + slot * i + slot / 2
            p.amount?.let {
                val h = (it * scaleY).toFloat()
                drawRoundRect(barColor, Offset(cx - barWidth / 2, size.height - h), Size(barWidth, h), CornerRadius(4.dp.toPx(), 4.dp.toPx()))
            }
            listOfNotNull(p.min, p.max).forEach { t ->
                val y = size.height - (t * scaleY).toFloat()
                drawLine(targetColor, Offset(cx - slot * 0.45f, y), Offset(cx + slot * 0.45f, y), 2.dp.toPx())
            }
        }
        if (showAverage) {
            val path = Path()
            var started = false
            points.forEachIndexed { i, p ->
                val avg = p.average ?: return@forEachIndexed
                val x = pad + slot * i + slot / 2
                val y = size.height - (avg * scaleY).toFloat()
                if (started) path.lineTo(x, y) else path.moveTo(x, y).also { started = true }
            }
            drawPath(path, lineColor, style = Stroke(2.dp.toPx()))
        }
    }
}

/** History: daily totals, 7-day average, targets and a summary (H-1 … H-4). */
@Composable
fun HistoryScreen() {
    var days by remember { mutableStateOf(30) }
    var nutrientId by remember { mutableStateOf<Long?>(null) }
    var showAverage by remember { mutableStateOf(true) }
    val today = LocalDate.now()
    val from = today.minusDays(days - 1L)
    val history = rememberLoad(days) { history(from.toString(), today.toString()) }
    val nutrients = (rememberLoad { nutrients() }.first as? LoadState.Loaded)?.data.orEmpty()
    val selected: NutrientDto? = nutrients.firstOrNull { it.id == nutrientId } ?: nutrients.firstOrNull()

    Screen(stringResource(R.string.history_title)) { modifier ->
        Column(modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(7, 30, 90, 365).forEach { d ->
                    FilterChip(selected = days == d, onClick = { days = d }, label = { Text(pluralStringResource(R.plurals.history_last_days, d, d)) })
                }
            }
            Dropdown(stringResource(R.string.history_nutrient), nutrients.map { Choice(it.id, it.name) }, selected?.id, { nutrientId = it })
            Loadable(history) { view ->
                val logged = view.days.count { it.entryCount > 0 }
                Muted(pluralStringResource(R.plurals.history_logged_days, logged, logged, view.days.size))
                if (selected != null && logged > 0) {
                    SectionCard(selected.name) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("■ " + stringResource(R.string.history_daily), color = StatusColors.series1Light, style = MaterialTheme.typography.labelMedium)
                            FilterChip(selected = showAverage, onClick = { showAverage = !showAverage }, label = { Text("— " + stringResource(R.string.history_average7)) })
                            Text("– " + stringResource(R.string.history_target), style = MaterialTheme.typography.labelMedium)
                        }
                        Chart(series(view, selected.id), showAverage, stringResource(R.string.history_chart_label, selected.name))
                        Row(Modifier.fillMaxWidth().padding(start = 44.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Muted(Format.date(view.from))
                            Muted(Format.date(view.to))
                        }
                    }
                }
                SectionCard(stringResource(R.string.history_summary)) {
                    view.summary.forEach { s ->
                        val n = nutrients.firstOrNull { it.id == s.nutrientId } ?: return@forEach
                        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(n.name, fontWeight = FontWeight.SemiBold)
                                Text(stringResource(R.string.history_avg_per_day, Format.amount(s.average, n)))
                            }
                            Muted(stringResource(R.string.history_range, Format.amount(s.min, n), Format.amount(s.max, n)))
                            if (s.daysWithTarget > 0) Muted(stringResource(R.string.history_within_target, s.daysWithinTarget, s.daysWithTarget))
                        }
                    }
                    Muted(stringResource(R.string.history_summary_hint))
                }
            }
        }
    }
}
