package io.snyk.plugin.net

import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import okhttp3.mockwebserver.MockWebServer
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import snyk.net.HttpClient

class SnykApiClientTest {
    @JvmField
    @Rule
    val server: MockWebServer = MockWebServer()

    private lateinit var apiClientUnderTest: SnykApiClient

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

        apiClientUnderTest = SnykApiClient(retrofit)
    }

    private val fakePayload = FalsePositivePayload(
        topic = "",
        message = "",
        context = FalsePositiveContext(
            issueId = "",
            userPublicId = "",
            startLine = 0,
            endLine = 0,
            primaryFilePath = "",
            vulnName = "",
            fileContents = ""
        )
    )

    @Test
    fun `reportFalsePositive should fail if 404 returned by backend`() {
        server.enqueueResponse("sast_feedback-fail-404.json", 404)

        assertFalse(apiClientUnderTest.reportFalsePositive(fakePayload))
    }

    @Test
    fun `reportFalsePositive should do 2 more retry on fail`() {
        var requestsCounter = 0
        (0..2).forEach { _ ->
            server.enqueueResponse("sast_feedback-fail-404.json", 404) { requestsCounter++ }
        }

        assertTrue(requestsCounter == 3)
    }

    @Test
    fun `reportFalsePositive should succeed if 200 returned by backend`() {
        server.enqueueResponse("sast_feedback-fail-404.json", 200)

        assertTrue(apiClientUnderTest.reportFalsePositive(fakePayload))
    }
}
