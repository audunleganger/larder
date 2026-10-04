package com.caloriecompanion.shared.domain

import com.caloriecompanion.shared.api.FoodUnitLink
import com.caloriecompanion.shared.api.Ingredient
import com.caloriecompanion.shared.api.NameTranslation
import com.caloriecompanion.shared.api.UnitKind
import com.caloriecompanion.shared.normalizeName
import java.text.Collator
import java.util.Locale

/** Something with a main name and optional names per language (L-5). */
interface Named {
    val id: Long
    val name: String
    /** Language code -> name in that language. */
    val translations: Map<String, NameTranslation>

    /** The name to show to a reader of [language]. */
    fun displayName(language: String?): String = pickTranslation(translations, language)?.name ?: name

    /** Every name this item is known by, normalized for comparison. */
    fun allNames(): Set<String> = (listOf(name) + translations.values.map { it.name }).mapTo(HashSet(), ::normalizeName)
}

/** Units and nutrients are shared by a server's users; the rest of [Owned] is as seen by one reader. */
interface Owned {
    /** The user who made it. */
    val ownerId: Long
    val ownerName: String
    /** One of the units or nutrients every user starts with; only admins can change these. */
    val builtIn: Boolean
    /** Hidden for the reader: left out of their lists and pickers, but still works where it's used. */
    val hidden: Boolean
    /** When it was made, in ms since 1970. */
    val createdAt: Long
    /** When and by whom it was last changed; null if never (the name also when unknown). */
    val updatedAt: Long?
    val updatedByName: String?

    /** Whether the user [userId] may change or delete it. */
    fun canEdit(userId: Long, isAdmin: Boolean): Boolean = isAdmin || (!builtIn && ownerId == userId)
}

data class UnitDef(
    override val id: Long,
    override val name: String,
    val kind: UnitKind,
    val baseFactor: Double?,
    override val hidden: Boolean = false,
    /** Plural ending of the main name (U-8). */
    val pluralSuffix: String = "",
    override val translations: Map<String, NameTranslation> = emptyMap(),
    override val ownerId: Long = 0,
    override val ownerName: String = "",
    override val builtIn: Boolean = false,
    override val createdAt: Long = 0,
    override val updatedAt: Long? = null,
    override val updatedByName: String? = null,
    /** The reader's position for it, if they have set an order; see [unitOrder]. */
    val sortOrder: Int? = null,
) : Named, Owned {
    fun displayPluralSuffix(language: String?): String = pickTranslation(translations, language)?.pluralSuffix ?: pluralSuffix
}

/**
 * Display order of units for a reader in [language]: the ones they show in their own order, or
 * alphabetically until they set one; then the hidden ones alphabetically.
 */
fun unitOrder(language: String?): Comparator<UnitDef> {
    val collator = Collator.getInstance(Locale.forLanguageTag(language ?: "en"))
    return compareBy<UnitDef>({ it.hidden }, { it.sortOrder ?: Int.MAX_VALUE })
        .thenComparing({ it.displayName(language) }, collator)
        .thenBy { it.id }
}

data class NutrientDef(
    override val id: Long,
    override val name: String,
    val measureUnit: String,
    val displayPrecision: Int,
    /** The reader's order (N-3); hidden nutrients come after the shown ones. */
    val sortOrder: Int,
    val parentId: Long?,
    override val hidden: Boolean = false,
    override val translations: Map<String, NameTranslation> = emptyMap(),
    override val ownerId: Long = 0,
    override val ownerName: String = "",
    override val builtIn: Boolean = false,
    override val createdAt: Long = 0,
    override val updatedAt: Long? = null,
    override val updatedByName: String? = null,
) : Named, Owned

data class FoodDef(
    override val id: Long,
    override val name: String,
    val refAmount: Double?,
    val refUnitId: Long?,
    val notes: String?,
    val archived: Boolean,
    /** Nutrient id -> amount per reference amount. */
    val nutrients: Map<Long, Double>,
    val links: List<FoodUnitLink>,
    override val translations: Map<String, NameTranslation> = emptyMap(),
    /** Version of the food's photo, or null without one (F-13). */
    val imageVersion: Long? = null,
    /**
     * Set for composite foods (F-10). In a loaded [Catalog], [refAmount], [refUnitId] and [nutrients]
     * of a composite food are then derived from its ingredients (see [Composites]).
     */
    val composite: CompositeDef? = null,
    /** When it was made, in ms since 1970, and by whom (its owner). */
    val createdAt: Long = 0,
    val ownerName: String = "",
    /** Left out of the food picker when logging, but usable as an ingredient. */
    val ingredientOnly: Boolean = false,
    /** Its tags (F-16). */
    val tagIds: List<Long> = emptyList(),
) : Named

/** A tag for grouping foods (F-16); per user, like foods. */
data class TagDef(
    override val id: Long,
    override val name: String,
    override val translations: Map<String, NameTranslation> = emptyMap(),
    val archived: Boolean = false,
    val createdAt: Long = 0,
    val updatedAt: Long? = null,
) : Named

/** A composite food's definition (F-10) and what was derived from it. */
data class CompositeDef(
    val ingredients: List<Ingredient>,
    /** How much the ingredients make, as set; null = their total weight. */
    val yieldAmount: Double?,
    val yieldUnitId: Long?,
    val logAsWhole: Boolean,
    /** The reference amount and nutrient values entered by hand, kept for if the food stops being composite. */
    val manualRefAmount: Double?,
    val manualRefUnitId: Long?,
    val manualNutrients: Map<Long, Double>,
    /** Derived: the ingredients' total weight in grams, if each converts to a mass. */
    val totalGrams: Double? = null,
    /** Derived: positions of ingredients whose amount can't be calculated (they contribute nothing). */
    val unresolved: Set<Int> = emptySet(),
    /** Derived: each ingredient's weight in grams, if it converts. */
    val grams: List<Double?> = emptyList(),
)

/** A user's complete catalog, loaded into memory for calculations: their foods, and every unit and nutrient. */
class Catalog(
    units: List<UnitDef>,
    nutrients: List<NutrientDef>,
    foods: List<FoodDef>,
) {
    val units: Map<Long, UnitDef> = units.associateBy { it.id }
    /** In display order: grouped, see [inDisplayOrder]. */
    val nutrients: List<NutrientDef> = nutrients.inDisplayOrder()
    val nutrientsById: Map<Long, NutrientDef> = this.nutrients.associateBy { it.id }
    val foods: Map<Long, FoodDef> = foods.associateBy { it.id }

    /** Nutrients shown in day views, totals and history (not hidden). */
    val displayedNutrients: List<NutrientDef> get() = nutrients.filter { !it.hidden }
}

/**
 * Display order of nutrients (N-3): main nutrients by sort order, each directly followed by its
 * sub-nutrients (one level) in their own sort order. A sub-nutrient can never drift away from its parent.
 */
fun List<NutrientDef>.inDisplayOrder(): List<NutrientDef> = groupedOrder(this, { it.id }, { it.parentId }, compareBy({ it.sortOrder }, { it.id }))

/** Orders [items] as groups: top-level items by [order], each followed by its children by [order]. */
fun <T> groupedOrder(items: List<T>, id: (T) -> Long, parentId: (T) -> Long?, order: Comparator<T>): List<T> {
    val sorted = items.sortedWith(order)
    val ids = sorted.mapTo(HashSet(), id)
    val isChild = { item: T -> parentId(item)?.let { it in ids } == true }
    val children = sorted.filter(isChild).groupBy { parentId(it) }
    return sorted.filterNot(isChild).flatMap { listOf(it) + children[id(it)].orEmpty() }
}
