package com.caloriecompanion.shared.domain

import com.caloriecompanion.shared.api.NameTranslation
import com.caloriecompanion.shared.api.UnitKind

/**
 * Names in several languages (L-5). Every food, unit and nutrient has a main name; it may also have a
 * name per supported language. The name shown is the one in the reader's language if there is one,
 * otherwise the main name. For a language the app has no translations for, an English name is
 * preferred over the main name.
 */
object Languages {
    /** Languages an item can have a name in; the same as the user interface languages. */
    val SUPPORTED = listOf("en", "nb")
    const val FALLBACK = "en"

    /** Maps a locale ("nb-NO", "no", "en_US") to a language code; Norwegian variants become bokmål. */
    fun of(locale: String?): String? {
        val primary = locale?.trim()?.lowercase()?.split('-', '_', ',', ';')?.firstOrNull()?.takeIf { it.isNotEmpty() } ?: return null
        return if (primary == "no" || primary == "nn") "nb" else primary
    }

    fun isNorwegian(locale: String?): Boolean = of(locale) == "nb"
}

/** Picks the translation to show for [language], or null for the main name. */
fun <T> pickTranslation(translations: Map<String, T>, language: String?): T? {
    if (language == null) return null
    return translations[language]
        ?: if (language !in Languages.SUPPORTED) translations[Languages.FALLBACK] else null
}

/**
 * The plural form a new unit name starts with (U-8). Abbreviations of standard units (g, ml) get none
 * (the same as the name). Norwegian names get "er" ("r" after a final e: skive → skiver). English names
 * get "s", "es" after s, x, z, ch and sh (pinch → pinches), and "ies" for a consonant before a final y
 * (berry → berries). The user can change it per unit and language.
 */
fun defaultPlural(name: String, kind: UnitKind, language: String?): String {
    val clean = name.trim()
    if (kind.isStandard || clean.isEmpty()) return ""
    val lower = clean.lowercase()
    return when {
        Languages.isNorwegian(language) -> clean + if (lower.endsWith("e")) "r" else "er"
        lower.length > 1 && lower.endsWith("y") && lower[lower.length - 2] !in "aeiou" -> clean.dropLast(1) + "ies"
        listOf("s", "x", "z", "ch", "sh").any { lower.endsWith(it) } -> clean + "es"
        else -> clean + "s"
    }
}

/** The label for [quantity] of a unit: the plural form unless the quantity is exactly 1, or when it's empty. */
fun unitLabel(name: String, plural: String, quantity: Double?): String =
    if (quantity == null || quantity == 1.0 || plural.isEmpty()) name else plural

/** Translations in the order of [Languages.SUPPORTED]. */
internal fun Map<String, NameTranslation>.inLanguageOrder(): List<NameTranslation> =
    Languages.SUPPORTED.mapNotNull { this[it] } + filterKeys { it !in Languages.SUPPORTED }.values
