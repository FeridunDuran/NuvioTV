package com.nuvio.tv.data.trailer

import org.junit.Assert.*
import org.junit.Test

class TrailerBackendConfigurationTest {
    @Test fun `placeholder backends must not issue requests`() {
        for (url in listOf("", "https://localhost/", "https://127.0.0.1/", "https://[::1]/", "invalid", "http://example.com/")) {
            assertFalse(url, isConfiguredTrailerBackend(url))
        }
        assertTrue(isConfiguredTrailerBackend("https://resolver.example.com/"))
    }
    @Test fun `physical low memory tier limits trailers despite large Java heap`() {
        assertEquals(720, com.nuvio.tv.core.player.TrailerVideoPolicy.maxTrailerVideoHeight(false, true))
        assertEquals(720, com.nuvio.tv.core.player.TrailerVideoPolicy.maxTrailerVideoHeight(true, true))
        assertEquals(1080, com.nuvio.tv.core.player.TrailerVideoPolicy.maxTrailerVideoHeight(false, false))
    }
}
