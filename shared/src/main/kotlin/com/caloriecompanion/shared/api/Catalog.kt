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
 * An item's name in one language. [plural] is only used for units (U-8); empty means the same as [name].
 * In responses, `name` fields of catalog items are the main name, and `displayName` is the name in
 * the reader's language; names in references (e.g. an entry's foodName) are always display names.
 */
@Serializable
data class NameTranslation(
    /** Language code, e.g. "nb". */
    val locale: String,
    val name: String,
    val plural: String = "",
)

// ---- Units ----

/**
 * Units and nutrients are shared by all users of a server. Each user sees the ones they choose: the
 * rest are [UnitDto.hidden] for them. Only the user who made one, and admins, can change it ([UnitDto.canEdit]).
 */
@Serializable
data class UnitDto(
    val id: Long,
    /** Main name. */
    val name: String,
    val kind: UnitKind,
    /** For mass/volume: size in g or ml. Null for custom units. */
    val baseFactor: Double?,
    /** Left out of the reader's lists and pickers; still works where it's already used. */
    val hidden: Boolean,
    /** One of the standard units every user starts with (U-3); only admins can change these. */
    val builtIn: Boolean,
    /** Username of the user who made it. */
    val createdBy: String,
    /** When it was made, in ms since 1970. */
    val createdAt: Long,
    /** When it was last changed, in ms since 1970; null if never. Hiding and showing don't count. */
    val updatedAt: Long?,
    /** Username of the user who last changed it; null if never, or unknown (imported). */
    val updatedBy: String?,
    /** Whether the reader may change or delete it. */
    val canEdit: Boolean,
    /** The main name's plural form, used when the quantity isn't 1 (U-8); empty: the same as the name. */
    val plural: String,
    val translations: List<NameTranslation>,
    /** The name and plural form in the reader's language. */
    val displayName: String,
    val displayPlural: String,
    /** The reader's position for it (U-10); null while they haven't set an order, and for hidden units. */
    val sortOrder: Int?,
)

/** The reader's order of the units they show (U-10), by id; shown units not listed follow in their current order. */
@Serializable
data class UnitOrderInput(
    val ids: List<Long>,
)

@Serializable
data class UnitInput(
    val name: String,
    val kind: UnitKind,
    val baseFactor: Double? = null,
    /** Null: the default for the kind and language (see defaultPlural); on update, unchanged. */
    val plural: String? = null,
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
    /** The reader's order (N-3); hidden nutrients come after the shown ones. */
    val sortOrder: Int,
    val parentId: Long?,
    /** Left out of the reader's totals, history and lists; foods keep their values. See [UnitDto]. */
    val hidden: Boolean,
    /** One of the nutrients every user starts with (N-2); only admins can change these. */
    val builtIn: Boolean,
    val createdBy: String,
    val createdAt: Long,
    val updatedAt: Long?,
    val updatedBy: String?,
    val canEdit: Boolean,
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

/**
 * Corrects who made a unit or nutrient and when, and when and by whom it was last changed; admins only.
 * Users are given by username. [createdBy] becomes the owner, who may then change it.
 */
@Serializable
data class MetadataInput(
    val createdAt: Long,
    val createdBy: String,
    val updatedAt: Long? = null,
    /** Needs [updatedAt]. */
    val updatedBy: String? = null,
)

/** Corrects when a food was made; admins only. Who made it is its owner and can't change. */
@Serializable
data class FoodMetadataInput(
    val createdAt: Long,
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
    val unitPlural: String,
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
    /** Made of other foods (F-10). refAmount/refUnitId are then how much it makes. */
    val composite: Boolean,
    /** Left out of the food picker when logging, but usable as an ingredient (F-15). */
    val ingredientOnly: Boolean,
    /** Its tags (F-16). */
    val tagIds: List<Long>,
    /**
     * Nutrient values per reference amount, by nutrient id (F-17); calculated for composite foods.
     * A nutrient without a value is left out.
     */
    val nutrients: Map<Long, Double>,
)

// ---- Tags (F-16) ----

@Serializable
data class TagDto(
    val id: Long,
    /** Main name. */
    val name: String,
    val translations: List<NameTranslation>,
    /** The name in the reader's language. */
    val displayName: String,
    /** Not offered when tagging a food; foods keep it. */
    val archived: Boolean,
    /** Number of foods with this tag, archived ones included. */
    val foodCount: Int,
    /** When it was made, in ms since 1970. */
    val createdAt: Long,
    /** Username of the user who made it (and owns it). */
    val createdBy: String,
    /** When its names were last changed, in ms since 1970; null if never. */
    val updatedAt: Long?,
)

@Serializable
data class TagInput(
    val name: String,
    /** Null: unchanged on update (none on create). */
    val translations: List<NameTranslation>? = null,
)

@Serializable
data class TagDetail(
    val tag: TagDto,
    /** The foods with this tag, by display name. */
    val foods: List<FoodRef>,
)

/** Corrects when a tag was made and last changed; admins only, for their own tags. */
@Serializable
data class TagMetadataInput(
    val createdAt: Long,
    val updatedAt: Long? = null,
)

// ---- Composite foods (F-10) ----

/** One ingredient of a composite food: [quantity] of [unitId] of food [foodId]. */
@Serializable
data class Ingredient(
    val foodId: Long,
    val unitId: Long,
    val quantity: Double,
)

/**
 * Makes a food composite (F-10): its nutrients are then calculated from [ingredients], and it is
 * [yieldAmount] of [yieldUnitId] (both null: the ingredients' total weight). An empty ingredient
 * list makes it a plain food again. Logging it logs each ingredient as its own entry, unless [logAsWhole].
 */
@Serializable
data class CompositeInput(
    val ingredients: List<Ingredient>,
    val yieldAmount: Double? = null,
    val yieldUnitId: Long? = null,
    val logAsWhole: Boolean = false,
)

/** A composite food's ingredient as shown: display names, and whether its amount can be calculated. */
@Serializable
data class IngredientView(
    val foodId: Long,
    val foodName: String,
    val foodImageVersion: Long?,
    val unitId: Long,
    val unitName: String,
    val unitPlural: String,
    val quantity: Double,
    /** The unit has no size for that food (or the food can't be calculated); it then contributes nothing. */
    val unresolved: Boolean,
    /** The ingredient's weight, if it converts to a mass. */
    val grams: Double?,
)

/** What a composite food is made of and what that adds up to (F-10). */
@Serializable
data class CompositeDetail(
    val ingredients: List<IngredientView>,
    /** How much the ingredients make: as set, or their total weight; null if that can't be calculated. */
    val yieldAmount: Double?,
    val yieldUnitId: Long?,
    /** True when the yield is the ingredients' total weight. */
    val yieldAutomatic: Boolean,
    /** Total weight of the ingredients in g, if all of them convert to a mass. */
    val totalGrams: Double?,
    /** Nutrients of the whole yield, summed over the ingredients. */
    val nutrients: List<FoodNutrientValue>,
    val logAsWhole: Boolean,
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
    /**
     * The food's ingredients if it's composite (F-10). Its refAmount, refUnitId and nutrients above are
     * then the values entered by hand, unused while it's composite (see [FoodDetail.composite]).
     */
    val composite: CompositeInput?,
    /** When it was made, in ms since 1970. */
    val createdAt: Long,
    /** Username of the user who made it (and owns it). */
    val createdBy: String,
    /** Left out of the food picker when logging, but usable as an ingredient (F-15). */
    val ingredientOnly: Boolean,
    /** Its tags (F-16). */
    val tagIds: List<Long>,
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
    /** Null: unchanged on update (not composite on create). */
    val composite: CompositeInput? = null,
    /** Left out of the food picker when logging (F-15). Null: unchanged on update (false on create). */
    val ingredientOnly: Boolean? = null,
    /** Its tags (F-16). Null: unchanged on update (none on create). */
    val tagIds: List<Long>? = null,
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
    val plural: String,
)

@Serializable
data class FoodEntryRef(
    val entryId: Long,
    val date: String,
    val time: String,
    val quantity: Double,
    val unitId: Long,
    val unitName: String,
    val unitPlural: String,
)

@Serializable
data class FoodDetail(
    val food: FoodDto,
    val usableUnits: List<UsableUnit>,
    /** Entries with this food, newest first. */
    val entries: List<FoodEntryRef>,
    /** Set when the food is composite (F-10). */
    val composite: CompositeDetail?,
    /** Composite foods that contain this food. */
    val usedIn: List<FoodRef>,
    /** Dates when this composite food was logged as its ingredients (F-10), newest first. */
    val loggedAsItemsOn: List<String>,
)
