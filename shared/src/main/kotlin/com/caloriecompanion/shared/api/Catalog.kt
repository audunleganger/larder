package com.caloriecompanion.shared.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class UnitKind {
    @SerialName("mass") MASS,
    @SerialName("volume") VOLUME,
    @SerialName("custom") CUSTOM,
    ;

    val isStandard: Boolean get() = this != CUSTOM
}

// ---- Units ----

@Serializable
data class UnitDto(
    val id: Long,
    val name: String,
    val kind: UnitKind,
    /** For mass/volume: size in g or ml. Null for custom units. */
    val baseFactor: Double?,
    val archived: Boolean,
)

@Serializable
data class UnitInput(
    val name: String,
    val kind: UnitKind,
    val baseFactor: Double? = null,
)

@Serializable
data class FoodRef(
    val id: Long,
    val name: String,
    val archived: Boolean,
)

@Serializable
data class UnitDetail(
    val unit: UnitDto,
    /** Foods that link this unit explicitly (or use it as reference unit). */
    val foods: List<FoodRef>,
    /** Foods that can use this standard unit through automatic conversion (U-4). */
    val implicitFoods: List<FoodRef>,
    /** Dates with at least one entry in this unit, newest first. */
    val dates: List<String>,
)

// ---- Nutrients ----

@Serializable
data class NutrientDto(
    val id: Long,
    val name: String,
    val measureUnit: String,
    val displayPrecision: Int,
    val sortOrder: Int,
    val parentId: Long?,
    val archived: Boolean,
)

@Serializable
data class NutrientInput(
    val name: String,
    val measureUnit: String,
    val displayPrecision: Int = 1,
    val parentId: Long? = null,
)

@Serializable
data class NutrientOrderInput(
    val ids: List<Long>,
)

@Serializable
data class NutrientFoodValue(
    val foodId: Long,
    val foodName: String,
    val foodArchived: Boolean,
    /** Amount per reference amount of the food. */
    val amount: Double,
    val refAmount: Double?,
    val refUnitName: String?,
)

@Serializable
data class NutrientEntryRef(
    val entryId: Long,
    val date: String,
    val time: String,
    val foodId: Long,
    val foodName: String,
    val quantity: Double,
    val unitName: String,
    /** This nutrient's amount in the entry; null when it can't be calculated. */
    val amount: Double?,
)

@Serializable
data class NutrientDetail(
    val nutrient: NutrientDto,
    val foods: List<NutrientFoodValue>,
    val entries: List<NutrientEntryRef>,
    val entriesTruncated: Boolean,
)

// ---- Foods ----

@Serializable
data class FoodNutrientValue(
    val nutrientId: Long,
    val amount: Double,
)

/** "1 unit = equalsAmount equalsUnit". Both equals fields null means linked but not yet sized. */
@Serializable
data class FoodUnitLink(
    val unitId: Long,
    val equalsAmount: Double? = null,
    val equalsUnitId: Long? = null,
)

@Serializable
data class FoodSummary(
    val id: Long,
    val name: String,
    val archived: Boolean,
    val refAmount: Double?,
    val refUnitId: Long?,
    /** Number of nutrients with a value. */
    val nutrientCount: Int,
)

@Serializable
data class FoodDto(
    val id: Long,
    val name: String,
    val refAmount: Double?,
    val refUnitId: Long?,
    val notes: String?,
    val archived: Boolean,
    val nutrients: List<FoodNutrientValue>,
    val units: List<FoodUnitLink>,
)

@Serializable
data class FoodInput(
    val name: String,
    val refAmount: Double? = null,
    val refUnitId: Long? = null,
    val notes: String? = null,
    val nutrients: List<FoodNutrientValue> = emptyList(),
    val units: List<FoodUnitLink> = emptyList(),
)

/** What a new food's reference amount is prefilled with (F-12). Both null when there is no suitable unit. */
@Serializable
data class FoodRefDefault(
    val refAmount: Double?,
    val refUnitId: Long?,
)

@Serializable
data class UsableUnit(
    val unitId: Long,
    val name: String,
    val kind: UnitKind,
    /** True if linked explicitly (or the reference unit); false if usable through automatic conversion. */
    val explicit: Boolean,
    /** How much of the food's reference unit one of this unit is; null if it can't be resolved. */
    val amountInRefUnit: Double?,
)

@Serializable
data class FoodEntryRef(
    val entryId: Long,
    val date: String,
    val time: String,
    val quantity: Double,
    val unitId: Long,
    val unitName: String,
)

@Serializable
data class FoodDetail(
    val food: FoodDto,
    val usableUnits: List<UsableUnit>,
    /** Entries with this food, newest first. */
    val entries: List<FoodEntryRef>,
)
