package com.caloriecompanion.shared

import com.caloriecompanion.shared.api.ConflictStrategy
import com.caloriecompanion.shared.api.ErrorCodes
import com.caloriecompanion.shared.api.ExportTag
import com.caloriecompanion.shared.api.FoodInput
import com.caloriecompanion.shared.api.NameTranslation
import com.caloriecompanion.shared.api.TagInput
import com.caloriecompanion.shared.api.TagMetadataInput
import com.caloriecompanion.shared.domain.AppException
import com.caloriecompanion.shared.domain.TagColors
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
    private val foods = FoodService(t.db, t.userId, now = { clock })

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
        // No food has Fruit any more, so it's gone.
        assertEquals(listOf("Snack"), tags.list(includeArchived = true).map { it.name })
    }

    @Test
    fun `a tag made from a food is given to it at once`() {
        val apple = foods.create(FoodInput("Apple")).id
        val tagged = foods.addTag(apple, TagInput("  Fruit "))
        val fruit = tags.list().single()
        assertEquals("Fruit", fruit.name)
        assertEquals(listOf(fruit.id), tagged.tagIds)
        assertEquals(1, fruit.foodCount)
        assertEquals(1_000L, fruit.createdAt)

        // An existing tag, by a name in any language, is given instead of a new one.
        tags.update(fruit.id, TagInput("Fruit", listOf(NameTranslation("nb", "Frukt"))))
        val pear = foods.create(FoodInput("Pear")).id
        assertEquals(listOf(fruit.id), foods.addTag(pear, TagInput("frukt")).tagIds)
        assertEquals(listOf(fruit.id), foods.addTag(pear, TagInput("Fruit")).tagIds)
        assertEquals(1, tags.list().size)
        expectCode(ErrorCodes.NAME_REQUIRED) { foods.addTag(pear, TagInput(" ")) }
        expectCode(ErrorCodes.NOT_FOUND) { foods.addTag(999, TagInput("Snack")) }
    }

    @Test
    fun `a tag goes when its last food loses it`() {
        val apple = foods.create(FoodInput("Apple")).id
        val pear = foods.create(FoodInput("Pear")).id
        val fruit = foods.addTag(apple, TagInput("Fruit")).tagIds.single()
        foods.addTag(pear, TagInput("Fruit"))
        val snack = foods.addTag(pear, TagInput("Snack")).tagIds.first { it != fruit }

        // Setting a food's tags saves them at once.
        assertEquals(listOf(fruit), foods.setTags(pear, listOf(fruit, fruit)).tagIds)
        assertEquals(listOf("Fruit"), tags.list(includeArchived = true).map { it.name })
        expectCode(ErrorCodes.NOT_FOUND) { foods.setTags(pear, listOf(snack)) }

        foods.setTags(apple, emptyList())
        assertEquals(1, tags.get(fruit).foodCount)
        foods.delete(pear)
        assertTrue(tags.list(includeArchived = true).isEmpty())
        assertEquals(0L, t.db.tagQueries.selectTagTranslations(t.userId).executeAsList().size.toLong())
    }

    @Test
    fun `archived tags stay on foods, and deleting a tag takes it off them`() {
        val apple = foods.create(FoodInput("Apple")).id
        val fruit = foods.addTag(apple, TagInput("Fruit")).tagIds.single()
        tags.setArchived(fruit, true)
        assertEquals(emptyList(), tags.list().map { it.name })
        assertEquals(listOf("Fruit"), tags.list(includeArchived = true).map { it.name })
        assertEquals(listOf(fruit), foods.get(apple).tagIds)

        tags.delete(fruit)
        assertTrue(tags.list(includeArchived = true).isEmpty())
        assertEquals(emptyList(), foods.get(apple).tagIds)
        expectCode(ErrorCodes.NOT_FOUND) { tags.delete(fruit) }
    }

    @Test
    fun `tags have a palette color or none`() {
        val apple = foods.create(FoodInput("Apple")).id
        val fruit = foods.addTag(apple, TagInput("Fruit")).tagIds.single()
        assertNull(tags.get(fruit).color)
        assertEquals("green", tags.setColor(fruit, "green").color)
        assertEquals(TagColors.ALL.size, TagColors.ALL.toSet().size)
        expectCode(ErrorCodes.VALIDATION) { tags.setColor(fruit, "#00ff00") }
        assertEquals("green", tags.get(fruit).color)
        assertNull(tags.setColor(fruit, null).color)
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
        val fruit = foods.addTag(foods.create(FoodInput("Apple")).id, TagInput("Fruit")).tagIds.single()
        tags.update(fruit, TagInput("Fruit", listOf(NameTranslation("nb", "Frukt"))))
        val old = foods.addTag(foods.create(FoodInput("Pear")).id, TagInput("Old")).tagIds.single()
        tags.setArchived(old, true)
        tags.setColor(old, "teal")
        val exported = TransferService(t.db, t.userId).export()
        assertEquals(listOf("Fruit"), exported.foods.first { it.name == "Apple" }.tags)
        assertEquals("teal", exported.tags.first { it.name == "Old" }.color)
        // A tag no food in the file has isn't imported.
        val file = exported.copy(tags = exported.tags + ExportTag("Unused"))

        val other = TestDb()
        val otherTags = TagService(other.db, other.userId)
        val otherFoods = FoodService(other.db, other.userId)
        otherFoods.addTag(otherFoods.create(FoodInput("Plum")).id, TagInput("Frukt"))
        val result = TransferService(other.db, other.userId).import(file, ConflictStrategy.SKIP)
        assertEquals(1, result.tags.created)
        assertEquals(1, result.tags.skipped)
        val imported = otherTags.list(includeArchived = true)
        assertEquals(listOf("Frukt", "Old"), imported.map { it.name })
        assertTrue(imported.first { it.name == "Old" }.archived)
        assertEquals("teal", imported.first { it.name == "Old" }.color)
        val apple = otherFoods.list().first { it.name == "Apple" }
        assertEquals(listOf(imported.first { it.name == "Frukt" }.id), apple.tagIds)
        assertEquals(1_000L, imported.first { it.name == "Old" }.createdAt)

        // A version 5 file doesn't know tags, so an overwritten food keeps its own.
        TransferService(t.db, t.userId).import(file.copy(version = 5, foods = file.foods.map { it.copy(tags = emptyList()) }), ConflictStrategy.OVERWRITE)
        assertEquals(listOf(fruit), foods.list().first { it.name == "Apple" }.tagIds)
    }
}
