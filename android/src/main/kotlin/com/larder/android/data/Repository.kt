package com.larder.android.data

import com.larder.shared.api.ConflictStrategy
import com.larder.shared.api.DayView
import com.larder.shared.api.EntryInput
import com.larder.shared.api.EntryView
import com.larder.shared.api.ExportFile
import com.larder.shared.api.FoodDetail
import com.larder.shared.api.FoodDto
import com.larder.shared.api.FoodInput
import com.larder.shared.api.FoodSummary
import com.larder.shared.api.HistoryView
import com.larder.shared.api.ImportResult
import com.larder.shared.api.NutrientDetail
import com.larder.shared.api.NutrientDto
import com.larder.shared.api.NutrientInput
import com.larder.shared.api.PreviewInput
import com.larder.shared.api.PreviewResult
import com.larder.shared.api.TargetDto
import com.larder.shared.api.TargetInput
import com.larder.shared.api.UnitDetail
import com.larder.shared.api.UnitDto
import com.larder.shared.api.UnitInput

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
    suspend fun units(includeHidden: Boolean = false): List<UnitDto>
    suspend fun unitDetail(id: Long): UnitDetail
    suspend fun createUnit(input: UnitInput): UnitDto
    suspend fun updateUnit(id: Long, input: UnitInput): UnitDto
    suspend fun hideUnit(id: Long, hidden: Boolean): UnitDto
    suspend fun deleteUnit(id: Long)

    suspend fun nutrients(includeHidden: Boolean = false): List<NutrientDto>
    suspend fun nutrientDetail(id: Long): NutrientDetail
    suspend fun createNutrient(input: NutrientInput): NutrientDto
    suspend fun updateNutrient(id: Long, input: NutrientInput): NutrientDto
    suspend fun reorderNutrients(ids: List<Long>): List<NutrientDto>
    suspend fun hideNutrient(id: Long, hidden: Boolean): NutrientDto
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
