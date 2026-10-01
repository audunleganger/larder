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

// ---- Names (L-5) ----

/**
 * An item's name in one language. [pluralSuffix] is only used for units (U-8).
 * In responses, `name` fields of catalog items are the main name, and `displayName` is the name in
 * the reader's language; names in references (e.g. an entry's foodName) are always display names.
 */
@Serializable
data class NameTranslation(
    /** Language code, e.g. "nb". */
    val locale: String,
    val name: String,
    val pluralSuffix: String = "",
)

// ---- Units ----

@Serializable
data class UnitDto(
    val id: Long,
    /** Main name. */
    val name: String,
    val kind: UnitKind,
    /** For mass/volume: size in g or ml. Null for custom units. */
    val baseFactor: Double?,
    val archived: Boolean,
    /** Appended to the main name when the quantity isn't 1 (U-8). */
    val pluralSuffix: String,
    val translations: List<NameTranslation>,
    /** The name and plural ending in the reader's language. */
    val displayName: String,
    val displayPluralSuffix: String,
)

@Serializable
data class UnitInput(
    val name: String,
    val kind: UnitKind,
    val baseFactor: Double? = null,
    /** Null: the default for the kind and language (see defaultPluralSuffix); on update, unchanged. */
    val pluralSuffix: String? = null,
    /** Null: unchanged on update (none on create). */
    val translations: List<NameTranslation>? = null,
)

@Serializable
data class FoodRef(
    val id: Long,
    /** Display name. */
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
    /** Main name. */
    val name: String,
    val measureUnit: String,
    val displayPrecision: Int,
    val sortOrder: Int,
    val parentId: Long?,
    val archived: Boolean,
    val translations: List<NameTranslation>,
    val displayName: String,
)

@Serializable
data class NutrientInput(
    val name: String,
    val measureUnit: String,
    val displayPrecision: Int = 1,
    val parentId: Long? = null,
    /** Null: unchanged on update (none on create). */
    val translations: List<NameTranslation>? = null,
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
    val unitPluralSuffix: String,
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
    /** Changes whenever the food's photo does; null when it has none (F-13). */
    val imageVersion: Long?,
)

@Serializable
data class FoodDto(
    val id: Long,
    /** Main name. */
    val name: String,
    val refAmount: Double?,
    val refUnitId: Long?,
    val notes: String?,
    val archived: Boolean,
    val nutrients: List<FoodNutrientValue>,
    val units: List<FoodUnitLink>,
    val translations: List<NameTranslation>,
    val displayName: String,
    /** Changes whenever the food's photo does; null when it has none (F-13). */
    val imageVersion: Long?,
)

/**
 * A food's photo (F-13), base64-encoded and already resized by the client: [image] for the food page
 * (at most about 1280 px) and a small square [thumbnail] for lists and pickers.
 */
@Serializable
data class FoodImageData(
    /** image/jpeg, image/png or image/webp; both images have this type. */
    val contentType: String,
    val image: String,
    val thumbnail: String,
)

@Serializable
data class FoodInput(
    val name: String,
    val refAmount: Double? = null,
    val refUnitId: Long? = null,
    val notes: String? = null,
    val nutrients: List<FoodNutrientValue> = emptyList(),
    val units: List<FoodUnitLink> = emptyList(),
    /** Null: unchanged on update (none on create). */
    val translations: List<NameTranslation>? = null,
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
    /** Display name. */
    val name: String,
    val kind: UnitKind,
    /** True if linked explicitly (or the reference unit); false if usable through automatic conversion. */
    val explicit: Boolean,
    /** How much of the food's reference unit one of this unit is; null if it can't be resolved. */
    val amountInRefUnit: Double?,
    val pluralSuffix: String,
)

@Serializable
data class FoodEntryRef(
    val entryId: Long,
    val date: String,
    val time: String,
    val quantity: Double,
    val unitId: Long,
    val unitName: String,
    val unitPluralSuffix: String,
)

@Serializable
data class FoodDetail(
    val food: FoodDto,
    val usableUnits: List<UsableUnit>,
    /** Entries with this food, newest first. */
    val entries: List<FoodEntryRef>,
)
