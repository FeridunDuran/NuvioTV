package com.nuvio.tv.core.di

import java.io.IOException
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import okio.GzipSink
import okio.buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AddonResponseSizeDiagnosticsTest {
    private val client = OkHttpClient.Builder()
        .addInterceptor(AddonResponseSizeDiagnostics(bodyLimitBytes = { 64 }, diagnosticsEnabled = { false }))
        .build()

    @Test
    fun `accepts an exact limit and rejects a larger content length`() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("x".repeat(64)))
            server.enqueue(MockResponse().setBody("x".repeat(65)))
            val request = Request.Builder().url(server.url("/")).build()

            client.newCall(request).execute().use { assertEquals(64, it.body.string().length) }
            assertThrows(IOException::class.java) {
                client.newCall(request).execute().use { it.body.string() }
            }
        }
    }

    @Test
    fun `bounds chunked compressed JSON by decoded bytes`() {
        val encoded = Buffer()
        GzipSink(encoded).buffer().use { it.writeUtf8("x".repeat(65)) }
        MockWebServer().use { server ->
            server.enqueue(MockResponse().addHeader("Content-Encoding", "gzip").setChunkedBody(encoded, 4))
            val request = Request.Builder().url(server.url("/")).build()

            assertThrows(IOException::class.java) {
                client.newCall(request).execute().use { it.body.string() }
            }
        }
    }
}
