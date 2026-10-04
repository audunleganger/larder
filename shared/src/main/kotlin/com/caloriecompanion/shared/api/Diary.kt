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
    val foodImageVersion: Long?,
    val unitId: Long,
    val unitName: String,
    val unitPlural: String,
    val quantity: Double,
    val date: String,
    val time: String,
    val note: String?,
    val nutrients: List<NutrientAmount>,
    val unresolved: Boolean,
    /** The composite food this entry was logged as part of (F-10). */
    val viaFoodId: Long?,
    val viaFoodName: String?,
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
    /** Average per day with data for this nutrient; null if there are none. */
    val average: Double?,
    val min: Double?,
    val max: Double?,
    /** Logged days where at least one entry has a value for this nutrient. */
    val loggedDays: Int,
    /** Logged days that have a target for this nutrient. */
    val daysWithTarget: Int,
    val daysWithinTarget: Int,
)

/** One entry's amount of a nutrient (H-5); null when it can't be calculated. */
@Serializable
data class EntryContribution(
    val entryId: Long,
    val foodId: Long,
    /** Display name. */
    val foodName: String,
    val time: String,
    val amount: Double?,
)

@Serializable
data class DayContributions(
    val date: String,
    /** In time order. */
    val entries: List<EntryContribution>,
)

/** How each entry contributed to one nutrient's daily totals, for splitting them by food (H-5). */
@Serializable
data class NutrientContributions(
    val nutrientId: Long,
    /** Only days with entries. */
    val days: List<DayContributions>,
)

@Serializable
data class HistoryView(
    val from: String,
    val to: String,
    /** Every date in the range, including days without entries (entryCount 0). */
    val days: List<HistoryDay>,
    val summary: List<NutrientSummary>,
)
