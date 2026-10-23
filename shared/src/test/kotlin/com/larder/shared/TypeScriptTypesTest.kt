package com.larder.shared

import com.larder.shared.api.AdminUserCreate
import com.larder.shared.api.AdminUserUpdate
import com.larder.shared.api.BackupResult
import com.larder.shared.api.DayView
import com.larder.shared.api.EntryInput
import com.larder.shared.api.ErrorCodes
import com.larder.shared.api.ErrorResponse
import com.larder.shared.api.ExportFile
import com.larder.shared.api.FoodDetail
import com.larder.shared.api.FoodInput
import com.larder.shared.api.FoodMetadataInput
import com.larder.shared.api.FoodRefDefault
import com.larder.shared.api.FoodSummary
import com.larder.shared.api.FoodTagsInput
import com.larder.shared.api.HealthResponse
import com.larder.shared.api.HistoryView
import com.larder.shared.api.ImportResult
import com.larder.shared.api.LocaleInput
import com.larder.shared.api.LoginInput
import com.larder.shared.api.LoginResult
import com.larder.shared.api.MetadataInput
import com.larder.shared.api.NutrientContributions
import com.larder.shared.api.NutrientDetail
import com.larder.shared.api.NutrientInput
import com.larder.shared.api.NutrientOrderInput
import com.larder.shared.api.PasswordChangeInput
import com.larder.shared.api.PreviewInput
import com.larder.shared.api.PreviewResult
import com.larder.shared.api.PhotoLinkInput
import com.larder.shared.api.SetupInput
import com.larder.shared.api.SetupStatus
import com.larder.shared.api.TagColorInput
import com.larder.shared.api.TagDetail
import com.larder.shared.api.TagDto
import com.larder.shared.api.TagInput
import com.larder.shared.api.TagMetadataInput
import com.larder.shared.api.TargetDto
import com.larder.shared.api.TargetInput
import com.larder.shared.api.UnitDetail
import com.larder.shared.api.UnitInput
import com.larder.shared.api.UnitOrderInput
import com.larder.shared.api.ConflictStrategy
import com.larder.shared.api.UserDto
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
        serializer<TagDto>(), serializer<TagInput>(), serializer<TagDetail>(), serializer<TagMetadataInput>(), serializer<TagColorInput>(), serializer<PhotoLinkInput>(), serializer<FoodTagsInput>(),
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
