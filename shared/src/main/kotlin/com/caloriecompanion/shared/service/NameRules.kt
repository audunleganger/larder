package com.caloriecompanion.shared.service

import com.caloriecompanion.shared.api.ErrorCodes
import com.caloriecompanion.shared.api.NameTranslation
import com.caloriecompanion.shared.domain.AppException
import com.caloriecompanion.shared.domain.Languages
import com.caloriecompanion.shared.domain.Named
import com.caloriecompanion.shared.domain.cleanName
import com.caloriecompanion.shared.domain.validation
import com.caloriecompanion.shared.normalizeName

/** Validation of item names across languages (F-2, L-5, U-8). */
internal object NameRules {
    private const val MAX_SUFFIX_LENGTH = 20

    /**
     * Cleans translations: supported languages only, at most one per language, names trimmed, and
     * entries with a blank name dropped (= no name in that language). Plural endings are kept only
     * [withSuffix] (units).
     */
    fun cleanTranslations(input: List<NameTranslation>, withSuffix: Boolean): List<NameTranslation> {
        val seen = HashSet<String>()
        return input.mapNotNull { translation ->
            val language = Languages.of(translation.locale)
            if (language == null || language !in Languages.SUPPORTED) validation("Unsupported language '${translation.locale}'")
            if (!seen.add(language)) validation("More than one name in the same language")
            if (translation.name.isBlank()) return@mapNotNull null
            NameTranslation(language, cleanName(translation.name), if (withSuffix) cleanSuffix(translation.pluralSuffix) else "")
        }
    }

    fun cleanSuffix(suffix: String): String {
        val clean = suffix.trim()
        if (clean.length > MAX_SUFFIX_LENGTH) validation("Plural ending is longer than $MAX_SUFFIX_LENGTH characters")
        return clean
    }

    /**
     * Fails with NAME_TAKEN if any of [names] already belongs to another of [items], in any language.
     * An item may use the same name in several of its own languages.
     */
    fun ensureFree(names: Collection<String>, selfId: Long?, items: Collection<Named>, what: String) {
        val wanted = names.associateBy(::normalizeName)
        for (item in items) {
            if (item.id == selfId) continue
            val clash = item.allNames().firstOrNull { it in wanted } ?: continue
            val name = wanted.getValue(clash)
            val owner = if (normalizeName(item.name) == clash) "" else " (it's a translation of '${item.name}')"
            throw AppException(ErrorCodes.NAME_TAKEN, "A $what named '$name' already exists$owner", 409, mapOf("id" to item.id))
        }
    }

    /** Translations that don't clash with other items' names; used where a clash shouldn't fail (import). */
    fun withoutClashes(translations: List<NameTranslation>, selfId: Long?, items: Collection<Named>, mainName: String): List<NameTranslation> {
        val taken = items.filter { it.id != selfId }.flatMapTo(HashSet()) { it.allNames() }
        return translations.filter { normalizeName(it.name) !in taken || normalizeName(it.name) == normalizeName(mainName) }
    }
}
