package io.snyk.plugin.net

import okhttp3.mockwebserver.MockWebServer
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.core.IsEqual.equalTo
import org.hamcrest.core.IsNull.nullValue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import snyk.net.HttpClient

class CliConfigServiceTest {
    @JvmField
    @Rule
    val server: MockWebServer = MockWebServer()

    private lateinit var cliConfigServiceUnderTest: CliConfigService

    @Before
    fun setUp() {
        val httpClient = HttpClient().apply {
            connectTimeout = 1
            readTimeout = 1
            writeTimeout = 1
        }
        val retrofit = Retrofit.Builder()
            .client(httpClient.build())
            .baseUrl(server.url("/").toString())
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        cliConfigServiceUnderTest = retrofit.create(CliConfigService::class.java)
    }

    @Test
    fun `sast should not contain org query param if org is null`() {
        server.enqueueResponse("sast_local-code-engine-disabled_200.json", 200)

        val request = cliConfigServiceUnderTest.sast().request()

        assertThat(request.url.queryParameter("org"), nullValue())
    }

    @Test
    fun `sast should contain org query param if org is specified`() {
        server.enqueueResponse("sast_local-code-engine-disabled_200.json", 200)

        val request = cliConfigServiceUnderTest.sast("my-cool-test-org").request()

        assertThat(request.url.queryParameter("org"), equalTo("my-cool-test-org"))
    }
}
