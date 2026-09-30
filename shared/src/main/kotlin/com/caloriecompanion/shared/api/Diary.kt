package com.caloriecompanion.shared.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class EntryInput(
    val foodId: Long,
    val unitId: Long,
    val quantity: Double,
    /** YYYY-MM-DD */
    val date: String,
    /** HH:MM */
    val time: String,
    val note: String? = null,
)

@Serializable
data class PreviewInput(
    val foodId: Long,
    val unitId: Long,
    val quantity: Double,
)

@Serializable
data class NutrientAmount(
    val nutrientId: Long,
    /** Null when the value is missing (C-3). */
    val amount: Double?,
)

@Serializable
data class PreviewResult(
    val nutrients: List<NutrientAmount>,
    /** The unit can't be converted to the food's reference amount; all values are missing. */
    val unresolved: Boolean,
)

@Serializable
data class EntryView(
    val id: Long,
    val foodId: Long,
    val foodName: String,
    val unitId: Long,
    val unitName: String,
    val quantity: Double,
    val date: String,
    val time: String,
    val note: String?,
    val nutrients: List<NutrientAmount>,
    val unresolved: Boolean,
)

@Serializable
enum class TargetStatus {
    @SerialName("none") NONE,
    @SerialName("below") BELOW,
    @SerialName("within") WITHIN,
    @SerialName("above") ABOVE,
}

@Serializable
data class NutrientTotal(
    val nutrientId: Long,
    val amount: Double,
    /** Entries that lack data for this nutrient and contribute 0. */
    val missingCount: Int,
    val targetMin: Double?,
    val targetMax: Double?,
    val status: TargetStatus,
)

@Serializable
data class DayView(
    val date: String,
    val entries: List<EntryView>,
    val totals: List<NutrientTotal>,
)

// ---- Targets ----

@Serializable
data class TargetDto(
    val id: Long,
    val nutrientId: Long,
    val min: Double?,
    val max: Double?,
    val effectiveFrom: String,
)

@Serializable
data class TargetInput(
    val nutrientId: Long,
    val min: Double? = null,
    val max: Double? = null,
    val effectiveFrom: String,
)

// ---- History ----

@Serializable
data class HistoryDay(
    val date: String,
    val entryCount: Int,
    val totals: List<NutrientTotal>,
)

@Serializable
data class NutrientSummary(
    val nutrientId: Long,
    /** Average per logged day; null if no days are logged. */
    val average: Double?,
    val min: Double?,
    val max: Double?,
    val loggedDays: Int,
    /** Logged days that have a target for this nutrient. */
    val daysWithTarget: Int,
    val daysWithinTarget: Int,
)

@Serializable
data class HistoryView(
    val from: String,
    val to: String,
    /** Every date in the range, including days without entries (entryCount 0). */
    val days: List<HistoryDay>,
    val summary: List<NutrientSummary>,
)
