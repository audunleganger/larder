package com.caloriecompanion.shared.domain

import kotlin.io.encoding.Base64

/** A stored photo or thumbnail (F-13). [version] changes whenever the photo does. */
class StoredImage(val contentType: String, val bytes: ByteArray, val version: Long)

/** Checks for photos sent by clients, which resize them before uploading (F-13). */
object ImageRules {
    val CONTENT_TYPES = setOf("image/jpeg", "image/png", "image/webp")
    const val MAX_IMAGE_BYTES = 3 * 1024 * 1024
    const val MAX_THUMBNAIL_BYTES = 300 * 1024

    /** Decodes base64 image data and checks that it is the given type and not too large. */
    fun decode(base64: String, contentType: String, maxBytes: Int, what: String): ByteArray {
        if (contentType !in CONTENT_TYPES) validation("Unsupported image type '$contentType'; use JPEG, PNG or WebP")
        val bytes = try {
            Base64.decode(base64)
        } catch (e: IllegalArgumentException) {
            validation("The $what isn't valid base64")
        }
        if (bytes.isEmpty()) validation("The $what is empty")
        if (bytes.size > maxBytes) validation("The $what is larger than ${maxBytes / 1024} KB")
        if (!matches(bytes, contentType)) validation("The $what isn't a $contentType file")
        return bytes
    }

    private fun matches(bytes: ByteArray, contentType: String): Boolean {
        fun startsWith(vararg prefix: Int, offset: Int = 0) =
            bytes.size >= offset + prefix.size && prefix.indices.all { bytes[offset + it] == prefix[it].toByte() }
        return when (contentType) {
            "image/jpeg" -> startsWith(0xFF, 0xD8, 0xFF)
            "image/png" -> startsWith(0x89, 0x50, 0x4E, 0x47)
            "image/webp" -> startsWith(0x52, 0x49, 0x46, 0x46) && startsWith(0x57, 0x45, 0x42, 0x50, offset = 8)
            else -> false
        }
    }

    fun encode(bytes: ByteArray): String = Base64.encode(bytes)
}
