package com.caloriecompanion.shared

import com.caloriecompanion.shared.api.ConflictStrategy
import com.caloriecompanion.shared.api.EntryInput
import com.caloriecompanion.shared.api.ErrorCodes
import com.caloriecompanion.shared.api.ExportUnit
import com.caloriecompanion.shared.api.ExportUnitTranslation
import com.caloriecompanion.shared.api.FoodInput
import com.caloriecompanion.shared.api.NameTranslation
import com.caloriecompanion.shared.api.NutrientInput
import com.caloriecompanion.shared.api.UnitInput
import com.caloriecompanion.shared.api.UnitKind
import com.caloriecompanion.shared.domain.AppException
import com.caloriecompanion.shared.domain.Languages
import com.caloriecompanion.shared.domain.defaultPlural
import com.caloriecompanion.shared.domain.unitLabel
import com.caloriecompanion.shared.service.EntryService
import com.caloriecompanion.shared.service.FoodService
import com.caloriecompanion.shared.service.NutrientService
import com.caloriecompanion.shared.service.TransferService
import com.caloriecompanion.shared.service.UnitService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TranslationTest {
    private val t = TestDb()
    private fun units(language: String? = null) = UnitService(t.db, t.userId, language)
    private fun foods(language: String? = null) = FoodService(t.db, t.userId, language)
    private fun unit(name: String) = units().list(includeHidden = true).first { it.name == name }

    private fun expectCode(code: String, block: () -> Unit) {
        assertEquals(code, assertFailsWith<AppException> { block() }.code)
    }

    @Test
    fun `language codes are normalized`() {
        assertEquals("nb", Languages.of("nb-NO"))
        assertEquals("nb", Languages.of("no"))
        assertEquals("nb", Languages.of("nn"))
        assertEquals("en", Languages.of("en-US,en;q=0.9"))
        assertEquals(null, Languages.of(" "))
    }

    @Test
    fun `built-in names come in both languages`() {
        val serving = unit("serving")
        assertEquals(listOf(NameTranslation("nb", "porsjon", "porsjoner")), serving.translations)
        assertEquals("servings", serving.plural)
        assertEquals("porsjon", units("nb").get(serving.id).displayName)
        assertEquals("porsjoner", units("nb").get(serving.id).displayPlural)
        assertEquals("serving", units("en").get(serving.id).displayName)
        // Same in both languages: no translation.
        assertEquals(emptyList(), unit("g").translations)
        assertEquals("Karbohydrater", NutrientService(t.db, t.userId, "nb").list().first { it.name == "Carbohydrates" }.displayName)
    }

    @Test
    fun `built-in names are English with Norwegian translations, whatever the first user's language`() {
        val nb = TestDb("nb")
        val serving = UnitService(nb.db, nb.userId, "nb").list().first { it.name == "serving" }
        assertEquals(listOf(NameTranslation("nb", "porsjon", "porsjoner")), serving.translations)
        assertEquals("porsjon", serving.displayName)
    }

    @Test
    fun `the reader's language picks the name, falling back to the main name`() {
        val food = foods().create(FoodInput("Rye bread", translations = listOf(NameTranslation("nb", "Rugbrød"))))
        assertEquals("Rugbrød", foods("nb").get(food.id).displayName)
        assertEquals("Rye bread", foods("en").get(food.id).displayName)
        assertEquals("Rye bread", foods(null).get(food.id).displayName)
        // A language without translations prefers English, then the main name.
        assertEquals("Rye bread", foods("de").get(food.id).displayName)
        val norsk = foods().create(FoodInput("Brunost", translations = listOf(NameTranslation("en", "Brown cheese"))))
        assertEquals("Brunost", foods("nb").get(norsk.id).displayName)
        assertEquals("Brown cheese", foods("de").get(norsk.id).displayName)
        // Entries show display names too.
        val entry = EntryService(t.db, t.userId, language = "nb").create(EntryInput(food.id, unit("g").id, 50.0, "2026-10-01", "08:00"))
        assertEquals("Rugbrød", entry.foodName)
    }

    @Test
    fun `search matches every language and shows the display name`() {
        foods().create(FoodInput("Rye bread", translations = listOf(NameTranslation("nb", "Rugbrød"))))
        assertEquals(listOf("Rye bread"), foods("en").list("rugbr").map { it.name })
        assertEquals(listOf("Rugbrød"), foods("nb").list("rye").map { it.name })
    }

    @Test
    fun `a name belongs to one item in any language`() {
        foods().create(FoodInput("Rye bread", translations = listOf(NameTranslation("nb", "Rugbrød"))))
        expectCode(ErrorCodes.NAME_TAKEN) { foods().create(FoodInput("rugbrød")) }
        expectCode(ErrorCodes.NAME_TAKEN) { foods().create(FoodInput("Bread", translations = listOf(NameTranslation("nb", "Rye Bread")))) }
        // The same item may repeat its own name in another language.
        foods().create(FoodInput("Pizza", translations = listOf(NameTranslation("nb", "Pizza"))))
        // Units: "porsjon" is the Norwegian name of "serving".
        expectCode(ErrorCodes.NAME_TAKEN) { units().create(UnitInput("Porsjon", UnitKind.CUSTOM)) }
    }

    @Test
    fun `translations are validated`() {
        expectCode(ErrorCodes.VALIDATION) { foods().create(FoodInput("A", translations = listOf(NameTranslation("xx", "B")))) }
        expectCode(ErrorCodes.VALIDATION) {
            foods().create(FoodInput("A", translations = listOf(NameTranslation("nb", "B"), NameTranslation("no", "C"))))
        }
        // A blank name means "no name in that language".
        assertEquals(emptyList(), foods().create(FoodInput("A", translations = listOf(NameTranslation("nb", "  ")))).translations)
    }

    @Test
    fun `leaving translations out of an update keeps them`() {
        val food = foods().create(FoodInput("Rye bread", translations = listOf(NameTranslation("nb", "Rugbrød"))))
        foods().update(food.id, FoodInput("Rye bread", notes = "dark"))
        assertEquals(listOf(NameTranslation("nb", "Rugbrød")), foods().get(food.id).translations)
        foods().update(food.id, FoodInput("Rye bread", translations = emptyList()))
        assertEquals(emptyList(), foods().get(food.id).translations)
        val serving = unit("serving")
        units().update(serving.id, UnitInput("serving", UnitKind.CUSTOM))
        assertEquals("servings", unit("serving").plural)
        assertEquals("porsjon", unit("serving").translations.single().name)
    }

    @Test
    fun `plurals default by language and kind`() {
        assertEquals("slices", defaultPlural("slice", UnitKind.CUSTOM, "en"))
        assertEquals("pinches", defaultPlural("pinch", UnitKind.CUSTOM, "en"))
        assertEquals("glasses", defaultPlural("glass", UnitKind.CUSTOM, "en"))
        assertEquals("berries", defaultPlural("berry", UnitKind.CUSTOM, "en"))
        assertEquals("trays", defaultPlural("tray", UnitKind.CUSTOM, "en"))
        assertEquals("skiver", defaultPlural("skive", UnitKind.CUSTOM, "nb"))
        assertEquals("biter", defaultPlural("bit", UnitKind.CUSTOM, "nb"))
        assertEquals("", defaultPlural("oz", UnitKind.MASS, "en"))
        assertEquals("slices", units("en").create(UnitInput("slice", UnitKind.CUSTOM)).plural)
        assertEquals("skiver", units("nb").create(UnitInput("skive", UnitKind.CUSTOM)).plural)
        assertEquals("", units("en").create(UnitInput("sheep", UnitKind.CUSTOM, plural = "")).plural)
        assertEquals("geese", units("en").create(UnitInput("goose", UnitKind.CUSTOM, plural = " geese ")).plural)
    }

    @Test
    fun `labels use the plural unless the quantity is 1 or there is none`() {
        assertEquals("slices", unitLabel("slice", "slices", 2.0))
        assertEquals("slices", unitLabel("slice", "slices", 0.5))
        assertEquals("slice", unitLabel("slice", "slices", 1.0))
        assertEquals("geese", unitLabel("goose", "geese", 3.0))
        assertEquals("sheep", unitLabel("sheep", "", 3.0))
    }

    @Test
    fun `translations and plurals survive export and import`() {
        foods().create(FoodInput("Rye bread", translations = listOf(NameTranslation("nb", "Rugbrød"))))
        units().create(UnitInput("goose", UnitKind.CUSTOM, plural = "geese", translations = listOf(NameTranslation("nb", "gås", "gjess"))))
        NutrientService(t.db, t.userId).create(NutrientInput("Vitamin C", "mg", translations = listOf(NameTranslation("nb", "C-vitamin"))))
        val file = TransferService(t.db, t.userId).export()

        val other = TestDb()
        TransferService(other.db, other.userId).import(file, ConflictStrategy.OVERWRITE)
        assertEquals("Rugbrød", FoodService(other.db, other.userId, "nb").list().single().name)
        val goose = UnitService(other.db, other.userId).list().first { it.name == "goose" }
        assertEquals("geese", goose.plural)
        assertEquals(listOf(NameTranslation("nb", "gås", "gjess")), goose.translations)
        assertEquals("C-vitamin", NutrientService(other.db, other.userId, "nb").list().first { it.name == "Vitamin C" }.displayName)
    }

    @Test
    fun `plural endings in older files are read as name and ending`() {
        val file = TransferService(t.db, t.userId).export()
        val old = file.copy(
            version = 6,
            units = file.units + ExportUnit(
                "slice", UnitKind.CUSTOM, pluralSuffix = "s",
                translations = listOf(ExportUnitTranslation("nb", "skive", pluralSuffix = "r")),
            ) + ExportUnit("glass", UnitKind.CUSTOM),
        )
        TransferService(t.db, t.userId).import(old, ConflictStrategy.OVERWRITE)
        val slice = unit("slice")
        assertEquals("slices", slice.plural)
        assertEquals(listOf(NameTranslation("nb", "skive", "skiver")), slice.translations)
        assertEquals("", unit("glass").plural, "no ending: the same as the name")
    }

    @Test
    fun `an imported translation that clashes is dropped, not fatal`() {
        foods().create(FoodInput("Rugbrød"))
        val file = TransferService(t.db, t.userId).export()
        val incoming = file.copy(foods = file.foods + com.caloriecompanion.shared.api.ExportFood("Rye bread", translations = listOf(NameTranslation("nb", "Rugbrød"))))
        TransferService(t.db, t.userId).import(incoming, ConflictStrategy.SKIP)
        assertEquals(emptyList(), foods().list().first { it.name == "Rye bread" }.let { foods().get(it.id).translations })
    }
}
