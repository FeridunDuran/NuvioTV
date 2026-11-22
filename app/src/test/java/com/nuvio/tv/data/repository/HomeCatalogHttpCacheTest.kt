package com.nuvio.tv.data.repository

import com.nuvio.tv.data.remote.api.AddonApi
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.test.runTest
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

class HomeCatalogHttpCacheTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `Home cache preview uses matching URL and respects no store`() = runTest {
        Cache(temporaryFolder.newFolder(), 1024L * 1024).use { cache ->
            MockWebServer().use { server ->
                val api = Retrofit.Builder()
                    .baseUrl(server.url("/"))
                    .client(OkHttpClient.Builder().cache(cache).build())
                    .addConverterFactory(MoshiConverterFactory.create(
                        Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
                    ))
                    .build()
                    .create(AddonApi::class.java)
                val homeUrl = server.url("/profile-a/catalog/movie/home.json").toString()
                val otherUrl = server.url("/profile-b/catalog/movie/home.json").toString()
                val privateUrl = server.url("/profile-a/catalog/movie/private.json").toString()
                val body = """{"metas":[{"id":"tt1","name":"Movie"}]}"""
                server.enqueue(MockResponse().addHeader("Cache-Control", "max-age=0").setBody(body))
                server.enqueue(MockResponse().addHeader("Cache-Control", "no-store").setBody(body))

                assertEquals(200, api.getCatalog(homeUrl).code())
                assertEquals("tt1", api.getCachedCatalog(homeUrl).body()?.metas?.first()?.id)
                assertEquals(504, api.getCachedCatalog(otherUrl).code())
                assertEquals(200, api.getCatalog(privateUrl).code())
                api.getCachedCatalog(privateUrl).also {
                    assertEquals(504, it.code())
                    it.errorBody()?.close()
                }
                assertEquals(2, server.requestCount)
            }
        }
    }
}
