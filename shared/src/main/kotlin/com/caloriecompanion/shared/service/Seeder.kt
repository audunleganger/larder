package com.caloriecompanion.shared.service

import com.caloriecompanion.db.CalorieCompanionDatabase
import com.caloriecompanion.shared.api.NameTranslation
import com.caloriecompanion.shared.api.NutrientInput
import com.caloriecompanion.shared.api.UnitInput
import com.caloriecompanion.shared.api.UnitKind
import com.caloriecompanion.shared.domain.Languages
import com.caloriecompanion.shared.normalizeName

/**
 * The units (U-3) and nutrients (N-2) every user starts with. They are created once per server, with
 * English main names and Norwegian translations (L-5), and owned by the first user, normally the admin.
 */
object Seeder {
    private data class SeedUnit(val en: String, val nb: String, val kind: UnitKind, val factor: Double?, val enPlural: String = "", val nbPlural: String = "")
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
        SeedUnit("cup", "kopp", UnitKind.VOLUME, 250.0, "cups", "kopper"),
        SeedUnit("serving", "porsjon", UnitKind.CUSTOM, null, "servings", "porsjoner"),
        SeedUnit("piece", "stk", UnitKind.CUSTOM, null, "pieces"),
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

    fun isNorwegian(locale: String?): Boolean = Languages.isNorwegian(locale)

    /**
     * Sets up a new user: creates the built-in units and nutrients if the server has none yet (owned by
     * [userId]), and shows them for the user. Built-ins whose name is already taken are left out.
     */
    fun seed(db: CalorieCompanionDatabase, userId: Long) = db.transaction {
        val unitQueries = db.quantityUnitQueries
        val nutrientQueries = db.nutrientQueries
        if (unitQueries.selectBuiltInUnitIds().executeAsList().isEmpty()) {
            val service = UnitService(db, userId)
            val taken = loadUnits(db, userId).flatMapTo(HashSet()) { it.allNames() }
            for (seed in units) {
                if (normalizeName(seed.en) in taken || normalizeName(seed.nb) in taken) continue
                val translations = if (seed.nb != seed.en) listOf(NameTranslation("nb", seed.nb, seed.nbPlural)) else emptyList()
                service.insert(seed.en, seed.kind, seed.factor, seed.enPlural, translations, builtIn = true)
            }
        }
        if (nutrientQueries.selectBuiltInNutrients().executeAsList().isEmpty()) {
            val service = NutrientService(db, userId)
            val existing = loadNutrients(db, userId)
            val taken = existing.flatMapTo(HashSet()) { it.allNames() }
            val ids = existing.associateTo(HashMap()) { normalizeName(it.name) to it.id }
            for (seed in nutrients) {
                if (normalizeName(seed.en) in taken || normalizeName(seed.nb) in taken) continue
                val translations = if (seed.nb != seed.en) listOf(NameTranslation("nb", seed.nb)) else emptyList()
                val parentId = seed.parent?.let { ids[normalizeName(it)] }
                ids[normalizeName(seed.en)] = service.insert(NutrientInput(seed.en, seed.unit, seed.precision, parentId, translations), builtIn = true)
            }
        }
        unitQueries.selectBuiltInUnitIds().executeAsList().forEach { unitQueries.showUnit(userId, it, null) }
        nutrientQueries.selectBuiltInNutrients().executeAsList().forEach { nutrientQueries.showNutrient(userId, it.id, it.sort_order) }
    }
}

/** The implicit single user of Android local mode (A-5). */
object LocalUser {
    const val USERNAME = "local"

    /** Returns the local user's id, creating and seeding it on first use. */
    fun ensure(db: CalorieCompanionDatabase, locale: String?, now: Long = System.currentTimeMillis()): Long =
        db.transactionWithResult {
            db.appUserQueries.selectFirst().executeAsOneOrNull()?.id ?: run {
                // The only user, so it may also change the built-in units and nutrients.
                db.appUserQueries.insertUser(USERNAME, null, true, locale, now)
                val id = db.appUserQueries.lastInsertRowId().executeAsOne()
                Seeder.seed(db, id)
                id
            }
        }
}
