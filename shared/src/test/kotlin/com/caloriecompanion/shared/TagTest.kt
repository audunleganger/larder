package com.caloriecompanion.shared

import com.caloriecompanion.shared.api.ConflictStrategy
import com.caloriecompanion.shared.api.ErrorCodes
import com.caloriecompanion.shared.api.FoodInput
import com.caloriecompanion.shared.api.NameTranslation
import com.caloriecompanion.shared.api.TagInput
import com.caloriecompanion.shared.api.TagMetadataInput
import com.caloriecompanion.shared.domain.AppException
import com.caloriecompanion.shared.service.FoodService
import com.caloriecompanion.shared.service.Seeder
import com.caloriecompanion.shared.service.TagService
import com.caloriecompanion.shared.service.TransferService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Tags for foods (F-16). */
class TagTest {
    private val t = TestDb()
    private var clock = 1_000L
    private val tags = TagService(t.db, t.userId, now = { clock })
    private val foods = FoodService(t.db, t.userId)

    private fun user(name: String): Long {
        t.db.appUserQueries.insertUser(name, "x", false, "en", 0)
        val id = t.db.appUserQueries.lastInsertRowId().executeAsOne()
        Seeder.seed(t.db, id)
        return id
    }

    private fun expectCode(code: String, block: () -> Unit): AppException =
        assertFailsWith<AppException> { block() }.also { assertEquals(code, it.code) }

    @Test
    fun `tags have names per language, unique among the user's tags`() {
        val fruit = tags.create(TagInput("Fruit", listOf(NameTranslation("nb", "Frukt"))))
        assertEquals("Frukt", TagService(t.db, t.userId, "nb").get(fruit.id).displayName)
        assertEquals(1_000L, fruit.createdAt)
        assertEquals("local", fruit.createdBy)
        assertNull(fruit.updatedAt)
        expectCode(ErrorCodes.NAME_TAKEN) { tags.create(TagInput("fruit")) }
        expectCode(ErrorCodes.NAME_TAKEN) { tags.create(TagInput("Frukt")) }

        clock = 2_000
        val renamed = tags.update(fruit.id, TagInput("Fruits"))
        assertEquals(2_000L, renamed.updatedAt)
        assertEquals(listOf("Frukt"), renamed.translations.map { it.name })

        tags.create(TagInput("Dairy"))
        assertEquals(listOf("Dairy", "Fruits"), tags.list().map { it.name })
    }

    @Test
    fun `foods get tags, kept when an update leaves them out`() {
        val fruit = tags.create(TagInput("Fruit")).id
        val snack = tags.create(TagInput("Snack")).id
        val apple = foods.create(FoodInput("Apple", tagIds = listOf(snack, fruit, fruit))).id
        assertEquals(listOf(fruit, snack), foods.get(apple).tagIds)
        assertEquals(listOf(fruit, snack), foods.list().first { it.id == apple }.tagIds)
        assertEquals(1, tags.get(fruit).foodCount)
        assertEquals(listOf("Apple"), tags.detail(fruit).foods.map { it.name })

        foods.update(apple, FoodInput("Apple"))
        assertEquals(listOf(fruit, snack), foods.get(apple).tagIds)
        foods.update(apple, FoodInput("Apple", tagIds = listOf(snack)))
        assertEquals(listOf(snack), foods.get(apple).tagIds)
        expectCode(ErrorCodes.NOT_FOUND) { foods.update(apple, FoodInput("Apple", tagIds = listOf(999))) }
    }

    @Test
    fun `a tag in use is archived instead of deleted`() {
        val fruit = tags.create(TagInput("Fruit")).id
        val apple = foods.create(FoodInput("Apple", tagIds = listOf(fruit))).id
        expectCode(ErrorCodes.REFERENCED) { tags.delete(fruit) }
        tags.setArchived(fruit, true)
        assertEquals(emptyList(), tags.list().map { it.name })
        assertEquals(listOf("Fruit"), tags.list(includeArchived = true).map { it.name })
        // Foods keep it.
        assertEquals(listOf(fruit), foods.get(apple).tagIds)

        foods.delete(apple)
        tags.delete(fruit)
        assertTrue(tags.list(includeArchived = true).isEmpty())
    }

    @Test
    fun `tags belong to one user`() {
        val fruit = tags.create(TagInput("Fruit")).id
        val kari = user("kari")
        val theirs = TagService(t.db, kari)
        assertTrue(theirs.list().isEmpty())
        theirs.create(TagInput("Fruit"))
        expectCode(ErrorCodes.NOT_FOUND) { theirs.get(fruit) }
        expectCode(ErrorCodes.NOT_FOUND) { FoodService(t.db, kari).create(FoodInput("Pear", tagIds = listOf(fruit))) }
    }

    @Test
    fun `admins correct when their tags were made and changed`() {
        val fruit = tags.create(TagInput("Fruit")).id
        val corrected = tags.setMetadata(fruit, TagMetadataInput(500L, 700L))
        assertEquals(500L, corrected.createdAt)
        assertEquals(700L, corrected.updatedAt)
        expectCode(ErrorCodes.VALIDATION) { tags.setMetadata(fruit, TagMetadataInput(700L, 500L)) }
        val kari = user("kari")
        val theirs = TagService(t.db, kari).create(TagInput("Fruit")).id
        expectCode(ErrorCodes.FORBIDDEN) { TagService(t.db, kari).setMetadata(theirs, TagMetadataInput(500L)) }
    }

    @Test
    fun `export and import keep tags, matched by name in any language`() {
        val fruit = tags.create(TagInput("Fruit", listOf(NameTranslation("nb", "Frukt")))).id
        tags.create(TagInput("Old")).id.also { tags.setArchived(it, true) }
        foods.create(FoodInput("Apple", tagIds = listOf(fruit)))
        val file = TransferService(t.db, t.userId).export()
        assertEquals(listOf("Fruit"), file.foods.first { it.name == "Apple" }.tags)

        val other = TestDb()
        val otherTags = TagService(other.db, other.userId)
        otherTags.create(TagInput("Frukt"))
        val result = TransferService(other.db, other.userId).import(file, ConflictStrategy.SKIP)
        assertEquals(1, result.tags.created)
        assertEquals(1, result.tags.skipped)
        val imported = otherTags.list(includeArchived = true)
        assertEquals(listOf("Frukt", "Old"), imported.map { it.name })
        assertTrue(imported.first { it.name == "Old" }.archived)
        val apple = FoodService(other.db, other.userId).list().first { it.name == "Apple" }
        assertEquals(listOf(imported.first { it.name == "Frukt" }.id), apple.tagIds)
        assertEquals(1_000L, imported.first { it.name == "Old" }.createdAt)

        // A version 5 file doesn't know tags, so an overwritten food keeps its own.
        TransferService(t.db, t.userId).import(file.copy(version = 5, foods = file.foods.map { it.copy(tags = emptyList()) }), ConflictStrategy.OVERWRITE)
        assertEquals(listOf(fruit), foods.list().first { it.name == "Apple" }.tagIds)
    }
}
