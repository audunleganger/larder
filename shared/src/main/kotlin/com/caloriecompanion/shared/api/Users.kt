package com.caloriecompanion.shared.api

import kotlinx.serialization.Serializable

@Serializable
data class UserDto(
    val id: Long,
    val username: String,
    val isAdmin: Boolean,
    val isDisabled: Boolean,
    val locale: String?,
    val createdAt: Long,
)

@Serializable
data class SetupStatus(
    val needsSetup: Boolean,
)

@Serializable
data class SetupInput(
    val username: String,
    val password: String,
    val locale: String? = null,
)

@Serializable
data class LoginInput(
    val username: String,
    val password: String,
)

@Serializable
data class LoginResult(
    val token: String,
    val user: UserDto,
)

@Serializable
data class PasswordChangeInput(
    val currentPassword: String,
    val newPassword: String,
)

@Serializable
data class LocaleInput(
    val locale: String,
)

@Serializable
data class AdminUserCreate(
    val username: String,
    val password: String,
    val isAdmin: Boolean = false,
    val locale: String? = null,
)

@Serializable
data class AdminUserUpdate(
    val isAdmin: Boolean? = null,
    val isDisabled: Boolean? = null,
    val password: String? = null,
)

@Serializable
data class BackupResult(
    val file: String,
    val sizeBytes: Long,
)
