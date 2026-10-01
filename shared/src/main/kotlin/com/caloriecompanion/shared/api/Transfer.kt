package com.caloriecompanion.shared.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Portable export of one user's data (X-1). References use names, not IDs,
 * so the file can be imported into any catalog.
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

        /** 2: names per language and plural endings (L-5, U-8). */
        const val VERSION = 2
    }
}

@Serializable
data class ExportUnit(
    val name: String,
    val kind: UnitKind,
    val baseFactor: Double? = null,
    val archived: Boolean = false,
    val pluralSuffix: String = "",
    val translations: List<NameTranslation> = emptyList(),
)

@Serializable
data class ExportNutrient(
    val name: String,
    val measureUnit: String,
    val displayPrecision: Int = 1,
    val parent: String? = null,
    val archived: Boolean = false,
    val translations: List<NameTranslation> = emptyList(),
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
)

@Serializable
data class ExportEntry(
    val food: String,
    val unit: String,
    val quantity: Double,
    val date: String,
    val time: String,
    val note: String? = null,
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
