package com.caloriecompanion.shared.api

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Portable export of one user's data (X-1). References use names, not IDs, so the file can be imported
 * into any server. Units and nutrients are shared by a server's users: the file has the ones the user's
 * data uses or the user shows, whoever made them.
 */
@Serializable
data class ExportFile(
    val format: String = FORMAT,
    val version: Int = VERSION,
    val exportedAt: String,
    val units: List<ExportUnit>,
    val nutrients: List<ExportNutrient>,
    val foods: List<ExportFood>,
    val entries: List<ExportEntry>,
    val targets: List<ExportTarget>,
) {
    companion object {
        const val FORMAT = "calorie-companion-export"

        /** Format ids import accepts; if the app is renamed, the new id is added here and old files still import. */
        val ACCEPTED_FORMATS = listOf(FORMAT)

        /**
         * 2: names per language and plural endings (L-5, U-8), food photos (F-13), composite foods (F-10).
         * 3: units and nutrients are shared by a server's users; `hidden` replaces `archived` for them.
         */
        const val VERSION = 3
    }
}

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class ExportUnit(
    val name: String,
    val kind: UnitKind,
    val baseFactor: Double? = null,
    /** Hidden for the user who exported it. */
    val hidden: Boolean = false,
    val pluralSuffix: String = "",
    val translations: List<NameTranslation> = emptyList(),
    /** Version 2 files: archived, read as [hidden]. */
    @EncodeDefault(EncodeDefault.Mode.NEVER) val archived: Boolean = false,
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class ExportNutrient(
    val name: String,
    val measureUnit: String,
    val displayPrecision: Int = 1,
    val parent: String? = null,
    /** Hidden for the user who exported it. */
    val hidden: Boolean = false,
    val translations: List<NameTranslation> = emptyList(),
    /** Version 2 files: archived, read as [hidden]. */
    @EncodeDefault(EncodeDefault.Mode.NEVER) val archived: Boolean = false,
)

@Serializable
data class ExportFoodUnit(
    val unit: String,
    val equalsAmount: Double? = null,
    val equalsUnit: String? = null,
)

@Serializable
data class ExportFood(
    val name: String,
    val refAmount: Double? = null,
    val refUnit: String? = null,
    val notes: String? = null,
    val archived: Boolean = false,
    /** Nutrient name -> amount per reference amount. */
    val nutrients: Map<String, Double> = emptyMap(),
    val units: List<ExportFoodUnit> = emptyList(),
    val translations: List<NameTranslation> = emptyList(),
    val image: FoodImageData? = null,
    /** Composite foods (F-10): ingredients by name. Empty for plain foods. */
    val ingredients: List<ExportIngredient> = emptyList(),
    val yieldAmount: Double? = null,
    val yieldUnit: String? = null,
    val logAsWhole: Boolean = false,
)

@Serializable
data class ExportIngredient(
    val food: String,
    val unit: String,
    val quantity: Double,
)

@Serializable
data class ExportEntry(
    val food: String,
    val unit: String,
    val quantity: Double,
    val date: String,
    val time: String,
    val note: String? = null,
    /** The composite food it was logged as part of. */
    val via: String? = null,
)

@Serializable
data class ExportTarget(
    val nutrient: String,
    val min: Double? = null,
    val max: Double? = null,
    val effectiveFrom: String,
)

@Serializable
enum class ConflictStrategy {
    /** Keep the existing item with the same name. */
    @SerialName("skip") SKIP,
    /** Replace the existing item's fields with the imported ones. */
    @SerialName("overwrite") OVERWRITE,
}

@Serializable
data class ImportCounts(
    val created: Int = 0,
    val updated: Int = 0,
    val skipped: Int = 0,
)

@Serializable
data class ImportResult(
    val units: ImportCounts,
    val nutrients: ImportCounts,
    val foods: ImportCounts,
    /** created = imported, skipped = identical entry already existed. */
    val entries: ImportCounts,
    val targets: ImportCounts,
)
