package com.caloriecompanion.shared.service

import com.caloriecompanion.db.CalorieCompanionDatabase
import com.caloriecompanion.shared.api.ErrorCodes
import com.caloriecompanion.shared.api.FoodRef
import com.caloriecompanion.shared.api.NameTranslation
import com.caloriecompanion.shared.api.TagDetail
import com.caloriecompanion.shared.api.TagDto
import com.caloriecompanion.shared.api.TagInput
import com.caloriecompanion.shared.api.TagMetadataInput
import com.caloriecompanion.shared.domain.AppException
import com.caloriecompanion.shared.domain.TagDef
import com.caloriecompanion.shared.domain.cleanName
import com.caloriecompanion.shared.domain.inLanguageOrder
import com.caloriecompanion.shared.domain.notFound
import com.caloriecompanion.shared.domain.validation
import com.caloriecompanion.shared.normalizeName
import java.text.Collator
import java.util.Locale

/**
 * Tags for grouping foods (F-16). Like foods they belong to one user, with the same name rules (F-2,
 * L-5): archived instead of deleted while foods use them. [language]: the reader's language (L-5).
 */
class TagService(
    private val db: CalorieCompanionDatabase,
    private val userId: Long,
    private val language: String? = null,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val queries = db.tagQueries

    /** The user's tags by display name; archived ones only when [includeArchived]. */
    fun list(includeArchived: Boolean = false): List<TagDto> {
        val counts = foodCounts()
        val collator = Collator.getInstance(Locale.forLanguageTag(language ?: "en"))
        return all()
            .filter { includeArchived || !it.archived }
            .sortedWith(compareBy(collator) { it.displayName(language) })
            .map { it.toDto(counts[it.id] ?: 0) }
    }

    fun get(id: Long): TagDto = find(id).toDto(foodCounts()[id] ?: 0)

    fun detail(id: Long): TagDetail {
        val tag = get(id)
        val foods = loadCatalog(db, userId).foods.values
            .filter { id in it.tagIds }
            .map { FoodRef(it.id, it.displayName(language), it.archived) }
            .sortedBy { it.name.lowercase() }
        return TagDetail(tag, foods)
    }

    /** [createdAt] and [updatedAt]: as in an imported file; by default it's made now and never changed. */
    fun create(input: TagInput, archived: Boolean = false, createdAt: Long? = null, updatedAt: Long? = null): TagDto = db.transactionWithResult {
        val name = cleanName(input.name)
        val translations = NameRules.cleanTranslations(input.translations.orEmpty(), withPlural = false)
        NameRules.ensureFree(listOf(name) + translations.map { it.name }, null, all(), "tag")
        queries.insertTag(userId, name, normalizeName(name), archived, createdAt ?: now(), updatedAt)
        val id = db.appUserQueries.lastInsertRowId().executeAsOne()
        writeTranslations(id, translations)
        get(id)
    }

    /** A null translation list in [input] leaves the translations unchanged. */
    fun update(id: Long, input: TagInput): TagDto = db.transactionWithResult {
        val before = find(id)
        val name = cleanName(input.name)
        val translations = input.translations?.let { NameRules.cleanTranslations(it, withPlural = false) }
        NameRules.ensureFree(listOf(name) + (translations ?: before.translations.values).map { it.name }, id, all(), "tag")
        queries.updateTag(name, normalizeName(name), now(), id, userId)
        translations?.let { writeTranslations(id, it) }
        get(id)
    }

    /** Corrects when the tag was made and last changed; admins only, for their own tags (tags are private). */
    fun setMetadata(id: Long, input: TagMetadataInput): TagDto = db.transactionWithResult {
        MetadataRules.requireAdmin(Reader.of(db, userId, language))
        find(id)
        MetadataRules.validTime(input.createdAt, "Created")
        input.updatedAt?.let { MetadataRules.validTime(it, "Last changed") }
        if (input.updatedAt != null && input.updatedAt < input.createdAt) validation("It can't be changed before it was made")
        queries.setTagMetadata(input.createdAt, input.updatedAt, id, userId)
        get(id)
    }

    fun setArchived(id: Long, archived: Boolean): TagDto {
        find(id)
        queries.setTagArchived(archived, id, userId)
        return get(id)
    }

    /** Deletes a tag no food has; otherwise fails with REFERENCED. */
    fun delete(id: Long) = db.transaction {
        find(id)
        val foods = queries.countTagFoodRefs(id).executeAsOne()
        if (foods > 0) throw AppException(ErrorCodes.REFERENCED, "Tag is in use; archive it instead", 409, mapOf("foods" to foods))
        queries.deleteTag(id, userId)
    }

    private fun all(): List<TagDef> = loadTags(db, userId)

    private fun find(id: Long): TagDef = all().firstOrNull { it.id == id } ?: notFound("Tag")

    private fun foodCounts(): Map<Long, Int> =
        queries.selectFoodTags(userId).executeAsList().groupingBy { it.tag_id }.eachCount()

    private fun writeTranslations(id: Long, translations: List<NameTranslation>) {
        queries.deleteTagTranslations(id)
        translations.forEach { queries.insertTagTranslation(id, it.locale, it.name, normalizeName(it.name)) }
    }

    private fun TagDef.toDto(foodCount: Int) = TagDto(
        id = id,
        name = name,
        translations = translations.inLanguageOrder(),
        displayName = displayName(language),
        archived = archived,
        foodCount = foodCount,
        createdAt = createdAt,
        createdBy = db.appUserQueries.selectById(userId).executeAsOneOrNull()?.username.orEmpty(),
        updatedAt = updatedAt,
    )
}
