package com.caloriecompanion.shared.service

import com.caloriecompanion.db.CalorieCompanionDatabase
import com.caloriecompanion.shared.api.NutrientInput
import com.caloriecompanion.shared.api.UnitInput
import com.caloriecompanion.shared.api.UnitKind

/** Default units (U-3) and nutrients (N-2) for a new catalog, in the user's language (L-4). */
object Seeder {
    private data class SeedUnit(val en: String, val nb: String, val kind: UnitKind, val factor: Double?)
    private data class SeedNutrient(val en: String, val nb: String, val unit: String, val precision: Int, val parent: String? = null)

    private val units = listOf(
        SeedUnit("g", "g", UnitKind.MASS, 1.0),
        SeedUnit("kg", "kg", UnitKind.MASS, 1000.0),
        SeedUnit("mg", "mg", UnitKind.MASS, 0.001),
        SeedUnit("oz", "oz", UnitKind.MASS, 28.349523125),
        SeedUnit("lb", "lb", UnitKind.MASS, 453.59237),
        SeedUnit("ml", "ml", UnitKind.VOLUME, 1.0),
        SeedUnit("dl", "dl", UnitKind.VOLUME, 100.0),
        SeedUnit("l", "l", UnitKind.VOLUME, 1000.0),
        SeedUnit("tsp", "ts", UnitKind.VOLUME, 5.0),
        SeedUnit("tbsp", "ss", UnitKind.VOLUME, 15.0),
        SeedUnit("cup", "kopp", UnitKind.VOLUME, 250.0),
        SeedUnit("serving", "porsjon", UnitKind.CUSTOM, null),
        SeedUnit("piece", "stk", UnitKind.CUSTOM, null),
    )

    private val nutrients = listOf(
        SeedNutrient("Energy", "Energi", "kcal", 0),
        SeedNutrient("Protein", "Protein", "g", 1),
        SeedNutrient("Carbohydrates", "Karbohydrater", "g", 1),
        SeedNutrient("Sugars", "Sukkerarter", "g", 1, parent = "Carbohydrates"),
        SeedNutrient("Fat", "Fett", "g", 1),
        SeedNutrient("Saturated fat", "Mettet fett", "g", 1, parent = "Fat"),
        SeedNutrient("Fiber", "Kostfiber", "g", 1),
        SeedNutrient("Salt", "Salt", "g", 2),
    )

    fun isNorwegian(locale: String?): Boolean =
        locale != null && locale.lowercase().let { it.startsWith("nb") || it.startsWith("no") || it.startsWith("nn") }

    /** Seeds an empty catalog. Does nothing if the user already has units or nutrients. */
    fun seed(db: CalorieCompanionDatabase, userId: Long, locale: String?) = db.transaction {
        val norwegian = isNorwegian(locale)
        val unitService = UnitService(db, userId)
        val nutrientService = NutrientService(db, userId)
        if (unitService.list(includeArchived = true).isEmpty()) {
            units.forEach { unitService.create(UnitInput(if (norwegian) it.nb else it.en, it.kind, it.factor)) }
        }
        if (nutrientService.list(includeArchived = true).isEmpty()) {
            val ids = HashMap<String, Long>()
            nutrients.forEach { seed ->
                val parentId = seed.parent?.let { ids.getValue(it) }
                val created = nutrientService.create(NutrientInput(if (norwegian) seed.nb else seed.en, seed.unit, seed.precision, parentId))
                ids[seed.en] = created.id
            }
        }
    }
}

/** The implicit single user of Android local mode (A-5). */
object LocalUser {
    const val USERNAME = "local"

    /** Returns the local user's id, creating and seeding it on first use. */
    fun ensure(db: CalorieCompanionDatabase, locale: String?, now: Long = System.currentTimeMillis()): Long =
        db.transactionWithResult {
            db.appUserQueries.selectFirst().executeAsOneOrNull()?.id ?: run {
                db.appUserQueries.insertUser(USERNAME, null, false, locale, now)
                val id = db.appUserQueries.lastInsertRowId().executeAsOne()
                Seeder.seed(db, id, locale)
                id
            }
        }
}
