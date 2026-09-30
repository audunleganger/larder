package com.caloriecompanion.android.ui

import com.caloriecompanion.shared.api.NutrientDto
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

object Format {
    fun number(value: Double, maxDigits: Int = 2): String =
        NumberFormat.getNumberInstance(Locale.getDefault()).apply {
            maximumFractionDigits = maxDigits
            minimumFractionDigits = 0
        }.format(value)

    fun amount(value: Double?, nutrient: NutrientDto): String =
        if (value == null) "—" else "${number(value, nutrient.displayPrecision)} ${nutrient.measureUnit}"

    fun quantity(value: Double): String = number(value, 3)

    fun date(iso: String, style: FormatStyle = FormatStyle.MEDIUM): String =
        LocalDate.parse(iso).format(DateTimeFormatter.ofLocalizedDate(style).withLocale(Locale.getDefault()))

    fun dayTitle(date: LocalDate): String =
        date.format(DateTimeFormatter.ofPattern("EEEE d. MMMM", Locale.getDefault())).replaceFirstChar { it.uppercase() }

    /** Parses decimals typed with "," or "." (L-2). Null for empty input, NaN for invalid input. */
    fun parseDecimal(input: String): Double? {
        val cleaned = input.trim().replace(" ", "").replace(" ", "").replace(',', '.')
        if (cleaned.isEmpty()) return null
        return cleaned.toDoubleOrNull() ?: Double.NaN
    }

    /** A number for an editable field, in the locale's decimal separator and without grouping. */
    fun input(value: Double?): String {
        if (value == null) return ""
        val text = java.math.BigDecimal(value).setScale(6, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
        val separator = java.text.DecimalFormatSymbols.getInstance(Locale.getDefault()).decimalSeparator
        return text.replace('.', separator)
    }

    private val timePattern = Regex("^([01]?\\d|2[0-3])[:.]([0-5]\\d)$")

    /** Normalizes "8:05" / "08.05" to "08:05"; null if invalid. */
    fun parseTime(input: String): String? {
        val match = timePattern.matchEntire(input.trim()) ?: return null
        return "%02d:%s".format(match.groupValues[1].toInt(), match.groupValues[2])
    }
}
