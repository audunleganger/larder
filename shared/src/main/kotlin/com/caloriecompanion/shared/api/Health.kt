package com.caloriecompanion.shared.api

import kotlinx.serialization.Serializable

@Serializable
data class HealthResponse(
    val status: String,
    val version: String,
    val apiVersion: Int,
)
