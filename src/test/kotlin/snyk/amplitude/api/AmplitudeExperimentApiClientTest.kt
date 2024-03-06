package snyk.amplitude.api

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.services.SnykApplicationSettingsStateService
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.buffer
import okio.source
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
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
        every { pluginInfo.integrationEnvironment } returns "IntelliJ IDEA"
        every { pluginInfo.integrationEnvironmentVersion } returns "2020.3.2"
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
        val settings = mockk<SnykApplicationSettingsStateService>(relaxed = true)
        every { pluginSettings() } returns settings
        server.enqueueResponse("sdk-vardata_multiple-variants_200.json", 200)
        val amplitudeVariantService = clientUnderTest.variantService()

        val response = amplitudeVariantService.sdkVardata(ExperimentUser("random-user-id")).execute()

        // asserts
        assertEquals(200, response.code())

        val variants = response.body()
        assertNotNull(variants)
        assertEquals(2, variants?.size)

        val expectedFirstVariant = Variant(value = "on", payload = mapOf("message" to "random message"))
        val actualFirstVariant = variants?.entries?.first()
        assertEquals("first-experiment", actualFirstVariant?.key)
        assertEquals(expectedFirstVariant, actualFirstVariant?.value)

        val expectedLastVariant = Variant(value = "off")
        val actualLastVariant = variants?.entries?.last()
        assertEquals("second-experiment", actualLastVariant?.key)
        assertEquals(expectedLastVariant, actualLastVariant?.value)
    }

    @Test
    fun `sdkVardata should return 200 by empty user id`() {
        val settings = mockk<SnykApplicationSettingsStateService>(relaxed = true)
        every { pluginSettings() } returns settings
        server.enqueueResponse("sdk-vardata_empty-response_200.json", 200)
        val amplitudeVariantService = clientUnderTest.variantService()

        val response = amplitudeVariantService.sdkVardata(ExperimentUser("")).execute()

        assertEquals(200, response.code())
    }

    @Test
    fun `allVariants should return multiple variants`() {
        val settings = mockk<SnykApplicationSettingsStateService>(relaxed = true)
        every { pluginSettings() } returns settings
        server.enqueueResponse("sdk-vardata_multiple-variants_200.json", 200)

        val actualVariants = clientUnderTest.allVariants(ExperimentUser("random-user-id"))

        assertNotNull(actualVariants)
        assertEquals(2, actualVariants.size)
    }

    @Test
    fun `allVariants should return empty map by empty user id`() {
        server.enqueueResponse("sdk-vardata_empty-response_200.json", 200)

        val variants = clientUnderTest.allVariants(ExperimentUser(""))

        assertTrue(variants.isEmpty())
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
