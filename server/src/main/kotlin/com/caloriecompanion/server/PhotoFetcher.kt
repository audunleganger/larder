package com.caloriecompanion.server

import com.caloriecompanion.shared.api.ErrorCodes
import com.caloriecompanion.shared.domain.AppException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withTimeout
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI
import java.net.URISyntaxException
import java.net.UnknownHostException
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/** An image downloaded from a link, with the type read from its first bytes. */
class FetchedImage(val contentType: String, val bytes: ByteArray)

/**
 * Downloads a food photo from a link (F-13), for the web GUI to shrink and upload like a file. Only http(s)
 * links to public addresses are followed, checked again after each redirect, so the server can't be used to
 * reach the local network; the download is limited in size and time, and only image files are accepted,
 * judged by their first bytes. [allowed] decides which addresses may be fetched (tests and
 * CC_PHOTO_FETCH_ALLOW_PRIVATE loosen it). The address is checked when looked up, just before connecting; a
 * name server that answers differently a moment later could still slip past, which is accepted for a
 * personal server whose users are logged in.
 */
class PhotoFetcher(
    private val allowed: (URI, List<InetAddress>) -> Boolean = { _, addresses -> addresses.all(::isPublicAddress) },
    private val maxBytes: Int = MAX_BYTES,
    private val timeout: Duration = TIMEOUT,
    private val resolve: (String) -> List<InetAddress> = { InetAddress.getAllByName(it).toList() },
) {
    private val client = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NEVER)
        .connectTimeout(timeout)
        .build()

    suspend fun fetch(link: String): FetchedImage = try {
        withTimeout(timeout.toMillis()) { download(link) }
    } catch (e: TimeoutCancellationException) {
        failed("The download took longer than ${timeout.seconds} seconds")
    }

    private suspend fun download(link: String): FetchedImage {
        var uri = parse(link)
        repeat(MAX_REDIRECTS + 1) {
            check(uri)
            val request = HttpRequest.newBuilder(uri)
                .timeout(timeout)
                .header("Accept", "image/*")
                .header("User-Agent", "CalorieCompanion photo fetch")
                .GET()
                .build()
            val response = try {
                runInterruptible(Dispatchers.IO) { client.send(request, HttpResponse.BodyHandlers.ofInputStream()) }
            } catch (e: IOException) {
                failed("The address couldn't be reached")
            } catch (e: IllegalArgumentException) {
                refused("That isn't a web address")
            }
            response.body().use { body ->
                when (response.statusCode()) {
                    in 300..399 -> {
                        val location = response.headers().firstValue("Location").orElse(null) ?: failed("The address redirects nowhere")
                        uri = try {
                            uri.resolve(location.trim())
                        } catch (e: IllegalArgumentException) {
                            failed("The address redirects to an invalid address")
                        }
                    }
                    in 200..299 -> {
                        val length = response.headers().firstValueAsLong("Content-Length").orElse(-1)
                        if (length > maxBytes) tooLarge()
                        val bytes = readLimited(body)
                        val type = sniffImageType(bytes) ?: throw AppException(ErrorCodes.LINK_NOT_IMAGE, "The address isn't an image file")
                        return FetchedImage(type, bytes)
                    }
                    else -> failed("The address answered with HTTP ${response.statusCode()}")
                }
            }
        }
        failed("The address redirects too many times")
    }

    private fun parse(link: String): URI {
        val uri = try {
            URI(link.trim())
        } catch (e: URISyntaxException) {
            refused("That isn't a web address")
        }
        return uri
    }

    /** Refuses anything but http(s) to an allowed address. */
    private suspend fun check(uri: URI) {
        if (uri.scheme?.lowercase() !in setOf("http", "https")) refused("Only http and https addresses can be used")
        val host = uri.host ?: refused("That isn't a web address")
        if (uri.userInfo != null) refused("Addresses with a username or password can't be used")
        val addresses = try {
            runInterruptible(Dispatchers.IO) { resolve(host) }
        } catch (e: UnknownHostException) {
            failed("The address couldn't be found")
        }
        if (addresses.isEmpty() || !allowed(uri, addresses)) refused("Addresses on the local network can't be used")
    }

    private suspend fun readLimited(body: InputStream): ByteArray = runInterruptible(Dispatchers.IO) {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val read = try {
                body.read(buffer)
            } catch (e: IOException) {
                failed("The download was interrupted")
            }
            if (read < 0) break
            out.write(buffer, 0, read)
            if (out.size() > maxBytes) tooLarge()
        }
        out.toByteArray()
    }

    private fun refused(message: String): Nothing = throw AppException(ErrorCodes.LINK_REFUSED, message)

    private fun failed(message: String): Nothing = throw AppException(ErrorCodes.LINK_FAILED, message, 502)

    private fun tooLarge(): Nothing = throw AppException(ErrorCodes.LINK_TOO_LARGE, "The image is larger than ${maxBytes / (1024 * 1024)} MB")

    companion object {
        const val MAX_BYTES = 10 * 1024 * 1024
        val TIMEOUT: Duration = Duration.ofSeconds(10)
        const val MAX_REDIRECTS = 5
    }
}

/**
 * Whether [address] is on the public internet: not loopback, private (10/8, 172.16/12, 192.168/16, fc00::/7),
 * link-local, carrier-grade NAT (100.64/10, used by VPNs), multicast or unspecified.
 */
fun isPublicAddress(address: InetAddress): Boolean {
    if (address.isLoopbackAddress || address.isSiteLocalAddress || address.isLinkLocalAddress ||
        address.isAnyLocalAddress || address.isMulticastAddress
    ) return false
    val bytes = address.address
    val first = bytes[0].toInt() and 0xFF
    val second = bytes[1].toInt() and 0xFF
    return when (address) {
        is Inet6Address -> {
            // Unique local addresses, and IPv4 addresses embedded in IPv6 (::ffff:a.b.c.d), judged as IPv4.
            if (first and 0xFE == 0xFC) return false
            if (bytes.take(10).all { it == 0.toByte() } && bytes[10] == 0xFF.toByte() && bytes[11] == 0xFF.toByte()) {
                return isPublicAddress(InetAddress.getByAddress(bytes.copyOfRange(12, 16)))
            }
            true
        }
        // 0/8 ("this network"), 100.64/10 (carrier-grade NAT), 192.0.0/24, 198.18/15 (benchmarking), 240/4 (reserved).
        else -> !(first == 0 || (first == 100 && second in 64..127) || (first == 192 && second == 0 && (bytes[2].toInt() and 0xFF) == 0) ||
            (first == 198 && second in 18..19) || first >= 240)
    }
}

/**
 * The image type of [bytes] from their first bytes, for formats browsers can show: JPEG, PNG, GIF, WebP, AVIF,
 * HEIC (Safari) and BMP. Null for anything else, such as a web page or a video.
 */
fun sniffImageType(bytes: ByteArray): String? {
    fun at(offset: Int, vararg prefix: Int) =
        bytes.size >= offset + prefix.size && prefix.indices.all { bytes[offset + it] == prefix[it].toByte() }
    fun ascii(offset: Int, text: String) = at(offset, *text.map { it.code }.toIntArray())
    return when {
        at(0, 0xFF, 0xD8, 0xFF) -> "image/jpeg"
        at(0, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) -> "image/png"
        ascii(0, "GIF87a") || ascii(0, "GIF89a") -> "image/gif"
        ascii(0, "RIFF") && ascii(8, "WEBP") -> "image/webp"
        ascii(4, "ftypavif") || ascii(4, "ftypavis") -> "image/avif"
        ascii(4, "ftypheic") || ascii(4, "ftypheix") || ascii(4, "ftypmif1") -> "image/heic"
        ascii(0, "BM") && bytes.size > 14 -> "image/bmp"
        else -> null
    }
}
