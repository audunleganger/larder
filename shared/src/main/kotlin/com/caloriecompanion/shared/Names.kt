package com.caloriecompanion.shared

import java.util.Locale

/**
 * Normalized form used for the uniqueness check on food stuff, unit and nutrient names (F-2):
 * trimmed, inner whitespace collapsed, lowercased.
 */
fun normalizeName(name: String): String =
    name.trim().replace(Regex("\\s+"), " ").lowercase(Locale.ROOT)
