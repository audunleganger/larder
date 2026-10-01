package com.caloriecompanion.shared.service

import com.caloriecompanion.db.CalorieCompanionDatabase
import com.caloriecompanion.shared.api.ConflictStrategy
import com.caloriecompanion.shared.api.ErrorCodes
import com.caloriecompanion.shared.api.ExportEntry
import com.caloriecompanion.shared.api.ExportFile
import com.caloriecompanion.shared.api.ExportFood
import com.caloriecompanion.shared.api.CompositeInput
import com.caloriecompanion.shared.api.ExportFoodUnit
import com.caloriecompanion.shared.api.ExportIngredient
import com.caloriecompanion.shared.api.ExportNutrient
import com.caloriecompanion.shared.api.ExportTarget
import com.caloriecompanion.shared.api.ExportUnit
import com.caloriecompanion.shared.api.FoodInput
import com.caloriecompanion.shared.api.FoodNutrientValue
import com.caloriecompanion.shared.api.FoodUnitLink
import com.caloriecompanion.shared.api.ImportCounts
import com.caloriecompanion.shared.api.Ingredient
import com.caloriecompanion.shared.api.ImportResult
import com.caloriecompanion.shared.api.NameTranslation
import com.caloriecompanion.shared.api.NutrientInput
import com.caloriecompanion.shared.api.TargetInput
import com.caloriecompanion.shared.api.UnitInput
import com.caloriecompanion.shared.domain.AppException
import com.caloriecompanion.shared.domain.Languages
import com.caloriecompanion.shared.domain.Named
import com.caloriecompanion.shared.domain.inLanguageOrder
import com.caloriecompanion.shared.domain.normalizeTime
import com.caloriecompanion.shared.domain.parseDate
import com.caloriecompanion.shared.normalizeName
import java.time.Instant

/** Export and import of a user's complete data (X-1, X-2, X-3). */
class TransferService(
    private val db: CalorieCompanionDatabase,
    private val userId: Long,
    private val now: () -> Long = System::currentTimeMillis,
) {
    fun export(): ExportFile {
        val catalog = loadCatalog(db, userId)
        val unitName = { id: Long -> catalog.units.getValue(id).name }
        val nutrientName = { id: Long -> catalog.nutrientsById.getValue(id).name }
        return ExportFile(
            exportedAt = Instant.ofEpochMilli(now()).toString(),
            units = catalog.units.values.sortedBy { it.id }.map {
                ExportUnit(it.name, it.kind, it.baseFactor, it.archived, it.pluralSuffix, it.translations.inLanguageOrder())
            },
            nutrients = catalog.nutrients.map {
                ExportNutrient(it.name, it.measureUnit, it.displayPrecision, it.parentId?.let(nutrientName), it.archived, it.translations.inLanguageOrder())
            },
            foods = catalog.foods.values.sortedBy { it.id }.map { food ->
                // A composite food's own values are the ones entered by hand; the rest is recalculated on import.
                val composite = food.composite
                val refAmount = if (composite != null) composite.manualRefAmount else food.refAmount
                val refUnitId = if (composite != null) composite.manualRefUnitId else food.refUnitId
                ExportFood(
                    name = food.name,
                    refAmount = refAmount,
                    refUnit = refUnitId?.let(unitName),
                    notes = food.notes,
                    archived = food.archived,
                    nutrients = (composite?.manualNutrients ?: food.nutrients).entries.associate { (id, amount) -> nutrientName(id) to amount },
                    units = food.links.map { ExportFoodUnit(unitName(it.unitId), it.equalsAmount, it.equalsUnitId?.let(unitName)) },
                    translations = food.translations.inLanguageOrder(),
                    image = food.imageVersion?.let { FoodService(db, userId).imageData(food.id) },
                    ingredients = composite?.ingredients.orEmpty().map {
                        ExportIngredient(catalog.foods.getValue(it.foodId).name, unitName(it.unitId), it.quantity)
                    },
                    yieldAmount = composite?.yieldAmount,
                    yieldUnit = composite?.yieldUnitId?.let(unitName),
                    logAsWhole = composite?.logAsWhole ?: false,
                )
            },
            entries = db.entryQueries.selectAllEntries(userId).executeAsList().map {
                ExportEntry(
                    catalog.foods.getValue(it.food_id).name, unitName(it.unit_id), it.quantity, it.local_date, it.local_time, it.note,
                    via = it.via_food_id?.let { via -> catalog.foods[via]?.name },
                )
            },
            targets = TargetService(db, userId).list().map { ExportTarget(nutrientName(it.nutrientId), it.min, it.max, it.effectiveFrom) },
        )
    }

    /**
     * Imports [file], matching units, nutrients and foods by name. On a name conflict the existing item is
     * kept ([ConflictStrategy.SKIP]) or overwritten ([ConflictStrategy.OVERWRITE]). Entries identical to an
     * existing one are skipped, so importing the same file twice is harmless. All or nothing.
     */
    fun import(file: ExportFile, strategy: ConflictStrategy): ImportResult {
        if (file.format !in ExportFile.ACCEPTED_FORMATS) invalid("Not an export file from this app")
        if (file.version > ExportFile.VERSION) invalid("Export version ${file.version} is newer than supported (${ExportFile.VERSION})")
        return try {
            db.transactionWithResult { importInTransaction(file, strategy) }
        } catch (e: AppException) {
            if (e.code == ErrorCodes.INVALID_IMPORT) throw e
            throw AppException(ErrorCodes.INVALID_IMPORT, "Import failed: ${e.message}", 400)
        }
    }

    private fun importInTransaction(file: ExportFile, strategy: ConflictStrategy): ImportResult {
        val overwrite = strategy == ConflictStrategy.OVERWRITE
        val unitService = UnitService(db, userId)
        val nutrientService = NutrientService(db, userId)
        val foodService = FoodService(db, userId)

        // Units
        var unitCounts = ImportCounts()
        val unitIds = unitService.list(includeArchived = true).associateTo(HashMap()) { normalizeName(it.name) to it.id }
        for (unit in file.units) {
            val key = normalizeName(unit.name)
            val existing = unitIds[key]
            val translations = freeTranslations(unit.translations, existing, unit.name, withSuffix = true) { loadCatalog(db, userId).units.values }
            // Version 1 files have no plural endings: keep existing ones, use the default for new units.
            val suffix = if (file.version >= 2) unit.pluralSuffix else null
            val input = UnitInput(unit.name, unit.kind, unit.baseFactor, suffix, translations)
            unitCounts = when {
                existing == null -> {
                    unitIds[key] = unitService.create(input, unit.archived).id
                    unitCounts.copy(created = unitCounts.created + 1)
                }
                overwrite -> {
                    unitService.update(existing, input)
                    unitService.setArchived(existing, unit.archived)
                    unitCounts.copy(updated = unitCounts.updated + 1)
                }
                else -> unitCounts.copy(skipped = unitCounts.skipped + 1)
            }
        }
        fun unitId(name: String) = unitIds[normalizeName(name)] ?: invalid("Unknown unit '$name'")

        // Nutrients: create/update without parents first, then link parents.
        var nutrientCounts = ImportCounts()
        val nutrientIds = nutrientService.list(includeArchived = true).associateTo(HashMap()) { normalizeName(it.name) to it.id }
        val touched = ArrayList<ExportNutrient>()
        for (nutrient in file.nutrients) {
            val key = normalizeName(nutrient.name)
            val existing = nutrientIds[key]
            val translations = freeTranslations(nutrient.translations, existing, nutrient.name, withSuffix = false) { loadCatalog(db, userId).nutrients }
            val input = NutrientInput(nutrient.name, nutrient.measureUnit, nutrient.displayPrecision, translations = translations)
            nutrientCounts = when {
                existing == null -> {
                    nutrientIds[key] = nutrientService.create(input, nutrient.archived).id
                    touched += nutrient
                    nutrientCounts.copy(created = nutrientCounts.created + 1)
                }
                overwrite -> {
                    nutrientService.update(existing, input)
                    nutrientService.setArchived(existing, nutrient.archived)
                    touched += nutrient
                    nutrientCounts.copy(updated = nutrientCounts.updated + 1)
                }
                else -> nutrientCounts.copy(skipped = nutrientCounts.skipped + 1)
            }
        }
        fun nutrientId(name: String) = nutrientIds[normalizeName(name)] ?: invalid("Unknown nutrient '$name'")
        for (nutrient in touched) {
            val parent = nutrient.parent ?: continue
            // Translations were written above; null leaves them as they are.
            nutrientService.update(
                nutrientId(nutrient.name),
                NutrientInput(nutrient.name, nutrient.measureUnit, nutrient.displayPrecision, nutrientId(parent)),
            )
        }

        // Foods
        var foodCounts = ImportCounts()
        val touchedFoods = HashSet<String>()
        val foodIds = foodService.list(includeArchived = true).associateTo(HashMap()) { normalizeName(it.name) to it.id }
        for (food in file.foods) {
            val key = normalizeName(food.name)
            val existing = foodIds[key]
            val translations = freeTranslations(food.translations, existing, food.name, withSuffix = false) { loadCatalog(db, userId).foods.values }
            val input = FoodInput(
                name = food.name,
                refAmount = food.refAmount,
                refUnitId = food.refUnit?.let(::unitId),
                notes = food.notes,
                nutrients = food.nutrients.map { (name, amount) -> FoodNutrientValue(nutrientId(name), amount) },
                units = food.units.map { FoodUnitLink(unitId(it.unit), it.equalsAmount, it.equalsUnit?.let(::unitId)) },
                translations = translations,
            )
            if (existing == null || overwrite) touchedFoods += key
            foodCounts = when {
                existing == null -> {
                    val created = foodService.create(input, food.archived, rememberRef = false).id
                    foodIds[key] = created
                    food.image?.let { foodService.setImage(created, it) }
                    foodCounts.copy(created = foodCounts.created + 1)
                }
                overwrite -> {
                    foodService.update(existing, input, rememberRef = false)
                    foodService.setArchived(existing, food.archived)
                    food.image?.let { foodService.setImage(existing, it) }
                    foodCounts.copy(updated = foodCounts.updated + 1)
                }
                else -> foodCounts.copy(skipped = foodCounts.skipped + 1)
            }
        }
        fun foodId(name: String) = foodIds[normalizeName(name)] ?: invalid("Unknown food '$name'")

        // Composite foods, once all foods exist (an ingredient may come later in the file).
        for (food in file.foods) {
            if (food.ingredients.isEmpty()) continue
            val id = foodId(food.name)
            // Only foods created or overwritten by this import; skipped ones keep their own definition.
            if (normalizeName(food.name) !in touchedFoods) continue
            foodService.setComposite(
                id,
                CompositeInput(
                    ingredients = food.ingredients.map { Ingredient(foodId(it.food), unitId(it.unit), it.quantity) },
                    yieldAmount = food.yieldAmount?.takeIf { food.yieldUnit != null },
                    yieldUnitId = food.yieldUnit?.takeIf { food.yieldAmount != null }?.let(::unitId),
                    logAsWhole = food.logAsWhole,
                ),
            )
        }

        // Entries (appended; exact duplicates skipped)
        var entryCounts = ImportCounts()
        val timestamp = now()
        for (entry in file.entries) {
            val foodId = foodId(entry.food)
            val unitId = unitId(entry.unit)
            if (!entry.quantity.isFinite() || entry.quantity <= 0) invalid("Invalid quantity for '${entry.food}' on ${entry.date}")
            val date = parseDate(entry.date).toString()
            val time = normalizeTime(entry.time)
            val note = entry.note?.trim()?.takeIf { it.isNotEmpty() }
            val duplicates = db.entryQueries.countIdenticalEntries(userId, foodId, unitId, entry.quantity, date, time, note).executeAsOne()
            entryCounts = if (duplicates > 0) {
                entryCounts.copy(skipped = entryCounts.skipped + 1)
            } else {
                val via = entry.via?.let { foodIds[normalizeName(it)] }
                db.entryQueries.insertEntryVia(userId, foodId, unitId, entry.quantity, date, time, note, timestamp, timestamp, via)
                entryCounts.copy(created = entryCounts.created + 1)
            }
        }

        // Targets
        var targetCounts = ImportCounts()
        val targetService = TargetService(db, userId)
        val existingTargets = targetService.list().map { it.nutrientId to it.effectiveFrom }.toSet()
        for (target in file.targets) {
            val nutrientId = nutrientId(target.nutrient)
            val from = parseDate(target.effectiveFrom).toString()
            val exists = (nutrientId to from) in existingTargets
            if (exists && !overwrite) {
                targetCounts = targetCounts.copy(skipped = targetCounts.skipped + 1)
                continue
            }
            targetService.set(TargetInput(nutrientId, target.min, target.max, from))
            targetCounts = if (exists) targetCounts.copy(updated = targetCounts.updated + 1)
            else targetCounts.copy(created = targetCounts.created + 1)
        }

        return ImportResult(unitCounts, nutrientCounts, foodCounts, entryCounts, targetCounts)
    }

    /**
     * The imported translations of an item, minus any whose name already belongs to another item:
     * translations are optional, so a clash drops the translation instead of failing the import.
     */
    private fun freeTranslations(
        translations: List<NameTranslation>,
        selfId: Long?,
        mainName: String,
        withSuffix: Boolean,
        items: () -> Collection<Named>,
    ): List<NameTranslation> {
        val clean = NameRules.cleanTranslations(translations.filter { Languages.of(it.locale) in Languages.SUPPORTED }, withSuffix)
        return NameRules.withoutClashes(clean, selfId, items().filter { normalizeName(it.name) != normalizeName(mainName) }, mainName)
    }

    private fun invalid(message: String): Nothing = throw AppException(ErrorCodes.INVALID_IMPORT, message, 400)
}
