package com.caloriecompanion.shared.api

import kotlinx.serialization.Serializable

/** Error codes returned by the API; clients translate them. */
object ErrorCodes {
    const val VALIDATION = "VALIDATION"
    const val NAME_REQUIRED = "NAME_REQUIRED"
    const val NAME_TAKEN = "NAME_TAKEN"
    const val NOT_FOUND = "NOT_FOUND"
    const val REFERENCED = "REFERENCED"
    const val UNAUTHORIZED = "UNAUTHORIZED"
    const val FORBIDDEN = "FORBIDDEN"
    const val INVALID_CREDENTIALS = "INVALID_CREDENTIALS"
    const val SETUP_DONE = "SETUP_DONE"
    const val LAST_ADMIN = "LAST_ADMIN"
    const val WEAK_PASSWORD = "WEAK_PASSWORD"
    const val INVALID_IMPORT = "INVALID_IMPORT"
}

@Serializable
data class ErrorResponse(
    val error: String,
    val message: String,
    /**
     * For REFERENCED: counts of what still references the item, e.g. {"entries": 3}. For NAME_TAKEN by a
     * unit or nutrient: {"id": <the one with that name>}, so a hidden one can be shown instead.
     */
    val details: Map<String, Long> = emptyMap(),
)
