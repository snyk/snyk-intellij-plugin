package snyk.amplitude.api

import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.snyk.plugin.pluginSettings
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.buffer
import okio.source
import org.hamcrest.collection.IsMapWithSize.aMapWithSize
import org.hamcrest.collection.IsMapWithSize.anEmptyMap
import org.hamcrest.core.IsEqual.equalTo
import org.hamcrest.core.IsNull.notNullValue
import org.junit.After
import org.junit.Assert.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import snyk.net.HttpClient
import snyk.pluginInfo
import java.nio.charset.StandardCharsets
import java.util.UUID

class AmplitudeExperimentApiClientTest {
    @JvmField
    @Rule
    val server: MockWebServer = MockWebServer()

    private lateinit var clientUnderTest: AmplitudeExperimentApiClient

    @Before
    fun setUp() {
        clientUnderTest = AmplitudeExperimentApiClient.create(
            baseUrl = server.url("/").toString(),
            apiKey = ""
        )
        unmockkAll()
        mockkStatic("io.snyk.plugin.UtilsKt")
        mockkStatic("snyk.PluginInformationKt")

        every { pluginInfo.integrationName } returns "snyk"
        every { pluginInfo.integrationVersion } returns "1.2.3"
        every { pluginSettings().userAnonymousId } returns UUID.randomUUID().toString()
        every { pluginSettings().ignoreUnknownCA } returns false
        every { pluginSettings().customEndpointUrl } returns "https://amplitude.com"
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `sdkVardata should return 200 by multiple variants`() {
        server.enqueueResponse("sdk-vardata_multiple-variants_200.json", 200)
        val amplitudeVariantService = clientUnderTest.variantService()

        val response = amplitudeVariantService.sdkVardata(ExperimentUser("random-user-id")).execute()

        // asserts
        assertThat(response.code(), equalTo(200))

        val variants = response.body()
        assertThat(variants, notNullValue())
        assertThat(variants, aMapWithSize(2))

        val expectedFirstVariant = Variant(value = "on", payload = mapOf("message" to "random message"))
        val actualFirstVariant = variants?.entries?.first()
        assertThat(actualFirstVariant?.key, equalTo("first-experiment"))
        assertThat(actualFirstVariant?.value, equalTo(expectedFirstVariant))

        val expectedLastVariant = Variant(value = "off")
        val actualLastVariant = variants?.entries?.last()
        assertThat(actualLastVariant?.key, equalTo("second-experiment"))
        assertThat(actualLastVariant?.value, equalTo(expectedLastVariant))
    }

    @Test
    fun `sdkVardata should return 200 by empty user id`() {
        server.enqueueResponse("sdk-vardata_empty-response_200.json", 200)
        val amplitudeVariantService = clientUnderTest.variantService()

        val response = amplitudeVariantService.sdkVardata(ExperimentUser("")).execute()

        assertThat(response.code(), equalTo(200))
    }

    @Test
    fun `allVariants should return multiple variants`() {
        server.enqueueResponse("sdk-vardata_multiple-variants_200.json", 200)

        val actualVariants = clientUnderTest.allVariants(ExperimentUser("random-user-id"))

        assertThat(actualVariants, notNullValue())
        assertThat(actualVariants, aMapWithSize(2))
    }

    @Test
    fun `allVariants should return empty map by empty user id`() {
        server.enqueueResponse("sdk-vardata_empty-response_200.json", 200)

        val variants = clientUnderTest.allVariants(ExperimentUser(""))

        assertThat(variants, anEmptyMap())
    }
}

internal fun MockWebServer.enqueueResponse(fileName: String, statusCode: Int) {
    val inputStream = javaClass.classLoader?.getResourceAsStream("text-fixtures/api-responses/amplitude/$fileName")

    val source = inputStream?.let { inputStream.source().buffer() }
    source?.let {
        enqueue(
            MockResponse()
                .setResponseCode(statusCode)
                .setBody(source.readString(StandardCharsets.UTF_8))
        )
    }
}
