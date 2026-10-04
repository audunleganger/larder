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
    /** Absent before version 6. */
    val tags: List<ExportTag> = emptyList(),
) {
    companion object {
        const val FORMAT = "calorie-companion-export"

        /** Format ids import accepts; if the app is renamed, the new id is added here and old files still import. */
        val ACCEPTED_FORMATS = listOf(FORMAT)

        /**
         * 2: names per language and plural endings (L-5, U-8), food photos (F-13), composite foods (F-10).
         * 3: units and nutrients are shared by a server's users; `hidden` replaces `archived` for them.
         * 4: when units, nutrients and foods were made and last changed (`createdAt`, `updatedAt`).
         * 5: "ingredient only" foods (`ingredientOnly`, F-15).
         * 6: tags (`tags`, and `tags` on foods by name, F-16).
         * 7: full plural forms of units (`plural`) instead of a plural ending (`pluralSuffix`, U-8).
         * 8: tag colors (`color`); only tags that foods have.
         */
        const val VERSION = 8
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
    /** Empty: the same as the name. */
    val plural: String = "",
    val translations: List<ExportUnitTranslation> = emptyList(),
    /** Versions 2 to 6: the plural ending, read as name + ending. */
    @EncodeDefault(EncodeDefault.Mode.NEVER) val pluralSuffix: String = "",
    /** Version 2 files: archived, read as [hidden]. */
    @EncodeDefault(EncodeDefault.Mode.NEVER) val archived: Boolean = false,
    /** ISO-8601 instant; kept when imported as a new item. Absent before version 4. */
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

/** A unit's name in one language; like [NameTranslation], plus the plural ending of older files. */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class ExportUnitTranslation(
    val locale: String,
    val name: String,
    val plural: String = "",
    /** Versions 2 to 6: the plural ending, read as name + ending. */
    @EncodeDefault(EncodeDefault.Mode.NEVER) val pluralSuffix: String = "",
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
    /** ISO-8601 instant; kept when imported as a new item. Absent before version 4. */
    val createdAt: String? = null,
    val updatedAt: String? = null,
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
    /** ISO-8601 instant; kept when imported as a new food. Absent before version 4. */
    val createdAt: String? = null,
    /** Left out of the food picker when logging (F-15). Absent before version 5. */
    val ingredientOnly: Boolean = false,
    /** Tag names (F-16). Absent before version 6. */
    val tags: List<String> = emptyList(),
)

@Serializable
data class ExportTag(
    val name: String,
    val archived: Boolean = false,
    val translations: List<NameTranslation> = emptyList(),
    /** ISO-8601 instants; kept when imported as a new tag. */
    val createdAt: String? = null,
    val updatedAt: String? = null,
    /** A palette color name; null: gray. Absent before version 8. */
    val color: String? = null,
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
    val tags: ImportCounts = ImportCounts(),
)
