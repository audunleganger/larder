package com.caloriecompanion.android.data

import com.caloriecompanion.shared.api.ConflictStrategy
import com.caloriecompanion.shared.api.DayView
import com.caloriecompanion.shared.api.EntryInput
import com.caloriecompanion.shared.api.EntryView
import com.caloriecompanion.shared.api.ExportFile
import com.caloriecompanion.shared.api.FoodDetail
import com.caloriecompanion.shared.api.FoodDto
import com.caloriecompanion.shared.api.FoodInput
import com.caloriecompanion.shared.api.FoodSummary
import com.caloriecompanion.shared.api.HistoryView
import com.caloriecompanion.shared.api.ImportResult
import com.caloriecompanion.shared.api.NutrientDetail
import com.caloriecompanion.shared.api.NutrientDto
import com.caloriecompanion.shared.api.NutrientInput
import com.caloriecompanion.shared.api.PreviewInput
import com.caloriecompanion.shared.api.PreviewResult
import com.caloriecompanion.shared.api.TargetDto
import com.caloriecompanion.shared.api.TargetInput
import com.caloriecompanion.shared.api.UnitDetail
import com.caloriecompanion.shared.api.UnitDto
import com.caloriecompanion.shared.api.UnitInput

/** Error from either backend, with the API's error code (see ErrorCodes) or [NETWORK]. */
class RepositoryException(
    val code: String,
    message: String,
    val details: Map<String, Long> = emptyMap(),
) : Exception(message) {
    companion object {
        const val NETWORK = "NETWORK"
    }
}

/**
 * Everything the UI needs, implemented by the on-device database ([LocalRepository], A-5)
 * or a server ([RemoteRepository]). The UI doesn't know which one it talks to.
 */
interface Repository {
    suspend fun units(includeArchived: Boolean = false): List<UnitDto>
    suspend fun unitDetail(id: Long): UnitDetail
    suspend fun createUnit(input: UnitInput): UnitDto
    suspend fun updateUnit(id: Long, input: UnitInput): UnitDto
    suspend fun archiveUnit(id: Long, archived: Boolean): UnitDto
    suspend fun deleteUnit(id: Long)

    suspend fun nutrients(includeArchived: Boolean = false): List<NutrientDto>
    suspend fun nutrientDetail(id: Long): NutrientDetail
    suspend fun createNutrient(input: NutrientInput): NutrientDto
    suspend fun updateNutrient(id: Long, input: NutrientInput): NutrientDto
    suspend fun reorderNutrients(ids: List<Long>): List<NutrientDto>
    suspend fun archiveNutrient(id: Long, archived: Boolean): NutrientDto
    suspend fun deleteNutrient(id: Long)

    suspend fun foods(query: String? = null, includeArchived: Boolean = false): List<FoodSummary>
    suspend fun foodDetail(id: Long): FoodDetail
    suspend fun createFood(input: FoodInput): FoodDto
    suspend fun updateFood(id: Long, input: FoodInput): FoodDto
    suspend fun archiveFood(id: Long, archived: Boolean): FoodDto
    suspend fun deleteFood(id: Long)

    suspend fun day(date: String): DayView
    suspend fun entry(id: Long): EntryView
    suspend fun preview(input: PreviewInput): PreviewResult
    suspend fun createEntry(input: EntryInput): EntryView
    suspend fun updateEntry(id: Long, input: EntryInput): EntryView
    suspend fun deleteEntry(id: Long)

    suspend fun targets(): List<TargetDto>
    suspend fun setTarget(input: TargetInput): TargetDto
    suspend fun deleteTarget(id: Long)

    suspend fun history(from: String, to: String): HistoryView

    suspend fun export(): ExportFile
    suspend fun import(file: ExportFile, strategy: ConflictStrategy): ImportResult
}
