package com.caloriecompanion.android.data

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import androidx.sqlite.db.SupportSQLiteDatabase
import com.caloriecompanion.db.CalorieCompanionDatabase
import com.caloriecompanion.shared.api.ConflictStrategy
import com.caloriecompanion.shared.api.EntryInput
import com.caloriecompanion.shared.api.ExportFile
import com.caloriecompanion.shared.api.FoodInput
import com.caloriecompanion.shared.api.NutrientInput
import com.caloriecompanion.shared.api.PreviewInput
import com.caloriecompanion.shared.api.TargetInput
import com.caloriecompanion.shared.api.UnitInput
import com.caloriecompanion.shared.domain.AppException
import com.caloriecompanion.shared.service.EntryService
import com.caloriecompanion.shared.service.FoodService
import com.caloriecompanion.shared.service.HistoryService
import com.caloriecompanion.shared.service.LocalUser
import com.caloriecompanion.shared.service.NutrientService
import com.caloriecompanion.shared.service.TargetService
import com.caloriecompanion.shared.service.TransferService
import com.caloriecompanion.shared.service.UnitService
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Local mode (A-5): the shared services running on an on-device SQLite database, for a single
 * implicit user. All database work happens on one background thread.
 */
class LocalRepository private constructor(
    private val db: CalorieCompanionDatabase,
    private val dispatcher: CoroutineDispatcher,
) : Repository {
    @Volatile private var userId: Long? = null

    private suspend fun <T> run(block: (userId: Long) -> T): T = withContext(dispatcher) {
        val user = userId ?: LocalUser.ensure(db, Locale.getDefault().toLanguageTag()).also { userId = it }
        try {
            block(user)
        } catch (e: AppException) {
            throw RepositoryException(e.code, e.message ?: e.code, e.details)
        }
    }

    override suspend fun units(includeArchived: Boolean) = run { UnitService(db, it).list(includeArchived) }
    override suspend fun unitDetail(id: Long) = run { UnitService(db, it).detail(id) }
    override suspend fun createUnit(input: UnitInput) = run { UnitService(db, it).create(input) }
    override suspend fun updateUnit(id: Long, input: UnitInput) = run { UnitService(db, it).update(id, input) }
    override suspend fun archiveUnit(id: Long, archived: Boolean) = run { UnitService(db, it).setArchived(id, archived) }
    override suspend fun deleteUnit(id: Long) = run { UnitService(db, it).delete(id) }

    override suspend fun nutrients(includeArchived: Boolean) = run { NutrientService(db, it).list(includeArchived) }
    override suspend fun nutrientDetail(id: Long) = run { NutrientService(db, it).detail(id) }
    override suspend fun createNutrient(input: NutrientInput) = run { NutrientService(db, it).create(input) }
    override suspend fun updateNutrient(id: Long, input: NutrientInput) = run { NutrientService(db, it).update(id, input) }
    override suspend fun reorderNutrients(ids: List<Long>) = run { NutrientService(db, it).reorder(ids) }
    override suspend fun archiveNutrient(id: Long, archived: Boolean) = run { NutrientService(db, it).setArchived(id, archived) }
    override suspend fun deleteNutrient(id: Long) = run { NutrientService(db, it).delete(id) }

    override suspend fun foods(query: String?, includeArchived: Boolean) = run { FoodService(db, it).list(query, includeArchived) }
    override suspend fun foodDetail(id: Long) = run { FoodService(db, it).detail(id) }
    override suspend fun createFood(input: FoodInput) = run { FoodService(db, it).create(input) }
    override suspend fun updateFood(id: Long, input: FoodInput) = run { FoodService(db, it).update(id, input) }
    override suspend fun archiveFood(id: Long, archived: Boolean) = run { FoodService(db, it).setArchived(id, archived) }
    override suspend fun deleteFood(id: Long) = run { FoodService(db, it).delete(id) }

    override suspend fun day(date: String) = run { EntryService(db, it).day(date) }
    override suspend fun entry(id: Long) = run { EntryService(db, it).get(id) }
    override suspend fun preview(input: PreviewInput) = run { EntryService(db, it).preview(input) }
    override suspend fun createEntry(input: EntryInput) = run { EntryService(db, it).create(input) }
    override suspend fun updateEntry(id: Long, input: EntryInput) = run { EntryService(db, it).update(id, input) }
    override suspend fun deleteEntry(id: Long) = run { EntryService(db, it).delete(id) }

    override suspend fun targets() = run { TargetService(db, it).list() }
    override suspend fun setTarget(input: TargetInput) = run { TargetService(db, it).set(input) }
    override suspend fun deleteTarget(id: Long) = run { TargetService(db, it).delete(id) }

    override suspend fun history(from: String, to: String) = run { HistoryService(db, it).history(from, to) }

    override suspend fun export() = run { TransferService(db, it).export() }
    override suspend fun import(file: ExportFile, strategy: ConflictStrategy) = run { TransferService(db, it).import(file, strategy) }

    companion object {
        const val DATABASE_NAME = "calorie-companion.db"

        fun open(context: Context): LocalRepository {
            val driver = AndroidSqliteDriver(
                schema = CalorieCompanionDatabase.Schema,
                context = context.applicationContext,
                name = DATABASE_NAME,
                callback = object : AndroidSqliteDriver.Callback(CalorieCompanionDatabase.Schema) {
                    override fun onConfigure(db: SupportSQLiteDatabase) {
                        db.setForeignKeyConstraintsEnabled(true)
                    }
                },
            )
            val dispatcher = Executors.newSingleThreadExecutor { Thread(it, "local-db") }.asCoroutineDispatcher()
            return LocalRepository(CalorieCompanionDatabase(driver), dispatcher)
        }
    }
}
