package com.caloriecompanion.server

import com.caloriecompanion.shared.api.ErrorCodes
import com.caloriecompanion.shared.domain.AppException
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import java.net.InetAddress
import java.net.InetSocketAddress
import java.time.Duration
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Photos from a link (F-13): what the server downloads, and what it refuses. */
class PhotoFetcherTest {
    private val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3)
    private val servers = mutableListOf<HttpServer>()

    /** A web server on localhost with [routes]: path -> handler. */
    private fun server(vararg routes: Pair<String, (HttpExchange) -> Unit>): String {
        val server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
        routes.forEach { (path, handler) -> server.createContext(path) { exchange -> exchange.use(handler) } }
        server.start()
        servers += server
        return "http://127.0.0.1:${server.address.port}"
    }

    private fun HttpExchange.send(status: Int, body: ByteArray, type: String = "application/octet-stream") {
        responseHeaders.add("Content-Type", type)
        sendResponseHeaders(status, if (body.isEmpty()) -1 else body.size.toLong())
        responseBody.write(body)
    }

    private fun HttpExchange.redirect(to: String) {
        responseHeaders.add("Location", to)
        sendResponseHeaders(302, -1)
    }

    @AfterTest
    fun stop() = servers.forEach { it.stop(0) }

    /** Allows localhost on the given ports only, as if they were public servers. */
    private fun fetcher(vararg ports: Int, maxBytes: Int = 1024, timeout: Duration = Duration.ofSeconds(2)) =
        PhotoFetcher(allowed = { uri, _ -> uri.port in ports }, maxBytes = maxBytes, timeout = timeout)

    private fun port(base: String) = base.substringAfterLast(':').toInt()

    private fun expectCode(code: String, block: suspend () -> Unit): AppException =
        assertFailsWith<AppException> { runBlocking { block() } }.also { assertEquals(code, it.code) }

    @Test
    fun `downloads an image, typed by its first bytes, following redirects`() = runBlocking {
        val base = server(
            "/photo" to { it.send(200, png, "text/plain") },
            "/moved" to { it.redirect("/photo") },
        )
        val image = fetcher(port(base)).fetch("$base/moved")
        assertEquals("image/png", image.contentType)
        assertContentEquals(png, image.bytes)
    }

    @Test
    fun `refuses what isn't an image`() {
        val base = server(
            "/page" to { it.send(200, "<!doctype html><html></html>".toByteArray(), "image/png") },
            "/gone" to { it.send(404, ByteArray(0)) },
        )
        expectCode(ErrorCodes.LINK_NOT_IMAGE) { fetcher(port(base)).fetch("$base/page") }
        expectCode(ErrorCodes.LINK_FAILED) { fetcher(port(base)).fetch("$base/gone") }
        expectCode(ErrorCodes.LINK_REFUSED) { fetcher(port(base)).fetch("file:///etc/passwd") }
        expectCode(ErrorCodes.LINK_REFUSED) { fetcher(port(base)).fetch("not a link") }
    }

    @Test
    fun `refuses the local network, also after a redirect`() {
        val inside = server("/photo" to { it.send(200, png) })
        val outside = server("/hop" to { it.redirect("$inside/photo") })
        // The default rule: localhost isn't public.
        expectCode(ErrorCodes.LINK_REFUSED) { PhotoFetcher().fetch("$inside/photo") }
        // Only the first server counts as public here; its redirect to the other is refused.
        expectCode(ErrorCodes.LINK_REFUSED) { fetcher(port(outside)).fetch("$outside/hop") }
        expectCode(ErrorCodes.LINK_REFUSED) { fetcher(port(inside)).fetch("http://user:pw@127.0.0.1:${port(inside)}/photo") }
    }

    @Test
    fun `limits size, redirects and time`() {
        val big = ByteArray(2048).also { png.copyInto(it) }
        val base = server(
            "/big" to { it.send(200, big) },
            // Chunked, so the size is only known while reading.
            "/stream" to { exchange ->
                exchange.sendResponseHeaders(200, 0)
                exchange.responseBody.write(big)
            },
            "/loop" to { it.redirect("/loop") },
            "/slow" to { exchange ->
                Thread.sleep(3_000)
                exchange.send(200, png)
            },
        )
        expectCode(ErrorCodes.LINK_TOO_LARGE) { fetcher(port(base)).fetch("$base/big") }
        expectCode(ErrorCodes.LINK_TOO_LARGE) { fetcher(port(base)).fetch("$base/stream") }
        expectCode(ErrorCodes.LINK_FAILED) { fetcher(port(base)).fetch("$base/loop") }
        val started = System.nanoTime()
        expectCode(ErrorCodes.LINK_FAILED) { fetcher(port(base), timeout = Duration.ofMillis(500)).fetch("$base/slow") }
        assertTrue(System.nanoTime() - started < 2_500_000_000L, "gave up in time")
    }

    @Test
    fun `public addresses`() {
        fun public(address: String) = isPublicAddress(InetAddress.getByName(address))
        listOf("8.8.8.8", "1.1.1.1", "2001:4860:4860::8888", "100.128.0.1").forEach { assertTrue(public(it), it) }
        listOf(
            "127.0.0.1", "10.0.0.10", "10.180.0.1", "172.16.5.4", "192.168.1.1", "169.254.169.254", "0.0.0.0",
            "100.64.0.1", "100.127.255.254", "224.0.0.1", "255.255.255.255", "::1", "::", "fe80::1", "fd00::1",
            "fc12::1", "::ffff:127.0.0.1", "::ffff:192.168.0.1",
        ).forEach { assertFalse(public(it), it) }
    }

    @Test
    fun `image types browsers show`() {
        fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }
        fun text(value: String, size: Int = 16) = value.toByteArray().copyOf(size)
        assertEquals("image/jpeg", sniffImageType(bytes(0xFF, 0xD8, 0xFF, 0xE0)))
        assertEquals("image/png", sniffImageType(png))
        assertEquals("image/gif", sniffImageType(text("GIF89a")))
        assertEquals("image/webp", sniffImageType(text("RIFF\u0000\u0000\u0000\u0000WEBPVP8 ")))
        assertEquals("image/avif", sniffImageType(byteArrayOf(0, 0, 0, 0x20) + text("ftypavif")))
        assertEquals("image/bmp", sniffImageType(text("BM", 32)))
        assertNull(sniffImageType(text("<!doctype html>")))
        assertNull(sniffImageType(byteArrayOf(0x1A, 0x45, 0xDF.toByte(), 0xA3.toByte()) + text("webm")))
        assertNull(sniffImageType(ByteArray(0)))
    }
}
