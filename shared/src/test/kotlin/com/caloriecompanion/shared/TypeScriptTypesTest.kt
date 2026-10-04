package com.caloriecompanion.shared

import com.caloriecompanion.shared.api.AdminUserCreate
import com.caloriecompanion.shared.api.AdminUserUpdate
import com.caloriecompanion.shared.api.BackupResult
import com.caloriecompanion.shared.api.DayView
import com.caloriecompanion.shared.api.EntryInput
import com.caloriecompanion.shared.api.ErrorCodes
import com.caloriecompanion.shared.api.ErrorResponse
import com.caloriecompanion.shared.api.ExportFile
import com.caloriecompanion.shared.api.FoodDetail
import com.caloriecompanion.shared.api.FoodInput
import com.caloriecompanion.shared.api.FoodMetadataInput
import com.caloriecompanion.shared.api.FoodRefDefault
import com.caloriecompanion.shared.api.FoodSummary
import com.caloriecompanion.shared.api.HealthResponse
import com.caloriecompanion.shared.api.HistoryView
import com.caloriecompanion.shared.api.ImportResult
import com.caloriecompanion.shared.api.LocaleInput
import com.caloriecompanion.shared.api.LoginInput
import com.caloriecompanion.shared.api.LoginResult
import com.caloriecompanion.shared.api.MetadataInput
import com.caloriecompanion.shared.api.NutrientContributions
import com.caloriecompanion.shared.api.NutrientDetail
import com.caloriecompanion.shared.api.NutrientInput
import com.caloriecompanion.shared.api.NutrientOrderInput
import com.caloriecompanion.shared.api.PasswordChangeInput
import com.caloriecompanion.shared.api.PreviewInput
import com.caloriecompanion.shared.api.PreviewResult
import com.caloriecompanion.shared.api.SetupInput
import com.caloriecompanion.shared.api.SetupStatus
import com.caloriecompanion.shared.api.TagDetail
import com.caloriecompanion.shared.api.TagDto
import com.caloriecompanion.shared.api.TagInput
import com.caloriecompanion.shared.api.TagMetadataInput
import com.caloriecompanion.shared.api.TargetDto
import com.caloriecompanion.shared.api.TargetInput
import com.caloriecompanion.shared.api.UnitDetail
import com.caloriecompanion.shared.api.UnitInput
import com.caloriecompanion.shared.api.UnitOrderInput
import com.caloriecompanion.shared.api.ConflictStrategy
import com.caloriecompanion.shared.api.UserDto
import kotlinx.serialization.descriptors.PolymorphicKind
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.serializer
import java.io.File
import java.lang.reflect.Modifier
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Generates web/src/api/types.gen.ts from the Kotlin API types, and fails when the checked-in file is stale.
 * Regenerate with: ./gradlew :shared:test -PupdateTsTypes=true
 */
class TypeScriptTypesTest {
    private val roots: List<SerialDescriptor> = listOf(
        serializer<HealthResponse>(), serializer<ErrorResponse>(),
        serializer<SetupStatus>(), serializer<SetupInput>(), serializer<LoginInput>(), serializer<LoginResult>(),
        serializer<UserDto>(), serializer<PasswordChangeInput>(), serializer<LocaleInput>(),
        serializer<AdminUserCreate>(), serializer<AdminUserUpdate>(), serializer<BackupResult>(),
        serializer<UnitInput>(), serializer<UnitOrderInput>(), serializer<UnitDetail>(),
        serializer<NutrientInput>(), serializer<NutrientOrderInput>(), serializer<NutrientDetail>(),
        serializer<FoodSummary>(), serializer<FoodInput>(), serializer<FoodDetail>(), serializer<FoodRefDefault>(),
        serializer<MetadataInput>(), serializer<FoodMetadataInput>(),
        serializer<TagDto>(), serializer<TagInput>(), serializer<TagDetail>(), serializer<TagMetadataInput>(),
        serializer<EntryInput>(), serializer<PreviewInput>(), serializer<PreviewResult>(), serializer<DayView>(),
        serializer<TargetDto>(), serializer<TargetInput>(), serializer<HistoryView>(), serializer<NutrientContributions>(),
        serializer<ExportFile>(), serializer<ImportResult>(), serializer<ConflictStrategy>(),
    ).map { it.descriptor }

    @Test
    fun `generated TypeScript types are up to date`() {
        val file = File("../web/src/api/types.gen.ts")
        val expected = generate()
        if (System.getProperty("updateTsTypes") == "true") {
            file.writeText(expected)
        }
        assertEquals(expected, file.takeIf { it.exists() }?.readText(), "types.gen.ts is stale; run ./gradlew :shared:test -PupdateTsTypes=true")
    }

    private fun generate(): String {
        val declarations = LinkedHashMap<String, String>()
        roots.forEach { reference(it, declarations) }
        val errorCodes = ErrorCodes::class.java.declaredFields
            .filter { Modifier.isStatic(it.modifiers) && it.type == String::class.java }
            .joinToString("\n") { "  ${it.name}: '${it.get(null)}'," }
        return buildString {
            appendLine("// Generated from the Kotlin API types in shared/ by TypeScriptTypesTest. Do not edit.")
            appendLine("// Regenerate: ./gradlew :shared:test -PupdateTsTypes=true")
            appendLine()
            appendLine("export const API_VERSION = ${AppInfo.API_VERSION}")
            appendLine()
            appendLine("export const ErrorCodes = {")
            appendLine(errorCodes)
            appendLine("} as const")
            appendLine()
            appendLine("export type ErrorCode = (typeof ErrorCodes)[keyof typeof ErrorCodes]")
            appendLine()
            appendLine("/** Export file format ids that import accepts. */")
            appendLine("export const EXPORT_FORMATS: readonly unknown[] = [${ExportFile.ACCEPTED_FORMATS.joinToString { "'$it'" }}]")
            declarations.toSortedMap().values.forEach { appendLine(); append(it) }
        }
    }

    private fun typeName(descriptor: SerialDescriptor) = descriptor.serialName.removeSuffix("?").substringAfterLast('.')

    /** Returns the TypeScript type expression for [descriptor], declaring named types as needed. */
    private fun reference(descriptor: SerialDescriptor, declarations: MutableMap<String, String>): String {
        val base = when (val kind = descriptor.kind) {
            PrimitiveKind.STRING, PrimitiveKind.CHAR -> "string"
            PrimitiveKind.BOOLEAN -> "boolean"
            is PrimitiveKind -> "number"
            SerialKind.ENUM -> {
                val name = typeName(descriptor)
                if (name !in declarations) {
                    declarations[name] = "export type $name = " +
                        (0 until descriptor.elementsCount).joinToString(" | ") { "'${descriptor.getElementName(it)}'" } + "\n"
                }
                name
            }
            StructureKind.LIST -> {
                val element = reference(descriptor.getElementDescriptor(0), declarations)
                if (element.contains(' ')) "($element)[]" else "$element[]"
            }
            StructureKind.MAP -> "Record<string, ${reference(descriptor.getElementDescriptor(1), declarations)}>"
            StructureKind.CLASS, StructureKind.OBJECT -> {
                val name = typeName(descriptor)
                if (name !in declarations) {
                    declarations[name] = "" // placeholder against recursion
                    val fields = (0 until descriptor.elementsCount).joinToString("") { i ->
                        val optional = if (descriptor.isElementOptional(i)) "?" else ""
                        "  ${descriptor.getElementName(i)}$optional: ${reference(descriptor.getElementDescriptor(i), declarations)}\n"
                    }
                    declarations[name] = "export interface $name {\n$fields}\n"
                }
                name
            }
            is PolymorphicKind, SerialKind.CONTEXTUAL -> error("Unsupported kind $kind in ${descriptor.serialName}")
        }
        return if (descriptor.isNullable) "$base | null" else base
    }
}
