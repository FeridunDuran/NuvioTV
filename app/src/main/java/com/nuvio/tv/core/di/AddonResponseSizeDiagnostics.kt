package com.nuvio.tv.core.di

import android.util.Log
import java.io.IOException
import com.nuvio.tv.core.perf.TurboPerf
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.ForwardingSource
import okio.buffer
import retrofit2.Invocation

/** Measures AddonApi bodies and optionally bounds decoded bytes before they reach Retrofit. */
internal class AddonResponseSizeDiagnostics(
    private val bodyLimitBytes: () -> Long? = {
        if (Log.isLoggable("NuvioAddonLimit", Log.DEBUG)) 8L * 1024 * 1024 else null
    },
    private val diagnosticsEnabled: () -> Boolean = { Log.isLoggable(TurboPerf.TAG, Log.DEBUG) }
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        val limit = bodyLimitBytes()
        val logSizes = diagnosticsEnabled()
        if (limit == null && !logSizes) return response
        val operation = request.tag(Invocation::class.java)?.method()?.name ?: "unknown"
        val body = response.body
        if (limit != null && body.contentLength() > limit) {
            response.close()
            throw IOException("Addon response exceeds the decoded byte limit")
        }
        val measuredSource = object : ForwardingSource(body.source()) {
            private var bytes = 0L
            private var reachedEnd = false
            private var reported = false

            override fun read(sink: Buffer, byteCount: Long): Long {
                if (byteCount == 0L) return 0L
                if (limit != null && bytes >= limit) {
                    if (bytes == limit && super.read(Buffer(), 1L) == -1L) {
                        reachedEnd = true
                        return -1L
                    }
                    bytes = limit + 1L
                    throw IOException("Addon response exceeds the decoded byte limit")
                }

                val permittedRead = limit?.let { byteCount.coerceAtMost(it - bytes) } ?: byteCount
                val read = super.read(sink, permittedRead)
                if (read == -1L) reachedEnd = true else bytes += read
                return read
            }

            override fun close() {
                if (logSizes && !reported) {
                    reported = true
                    Log.d(TurboPerf.TAG, "event=addon_response_size operation=$operation bytes=$bytes eof=$reachedEnd")
                }
                super.close()
            }
        }.buffer()
        return response.newBuilder().body(object : ResponseBody() {
            override fun contentType() = body.contentType()
            override fun contentLength() = body.contentLength()
            override fun source() = measuredSource
        }).build()
    }
}
