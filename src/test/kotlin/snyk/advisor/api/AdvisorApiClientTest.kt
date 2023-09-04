package snyk.advisor.api

import com.google.gson.Gson
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.snyk.plugin.services.SnykApplicationSettingsStateService
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.buffer
import okio.source
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import snyk.PluginInformation
import snyk.advisor.AdvisorPackageManager
import snyk.pluginInfo
import java.nio.charset.StandardCharsets

class AdvisorApiClientTest {
    private lateinit var clientUnderTest: AdvisorApiClient
    private val server = MockWebServer()
    private lateinit var settings: SnykApplicationSettingsStateService

    @Before
    fun setUp() {
        unmockkAll()
        mockkStatic("snyk.PluginInformationKt")
        val pluginInformation = PluginInformation(
            "testIntegrationName",
            "testIntegrationVersion",
            "testIntegrationEnvironment",
            "testIntegrationEnvironmentVersion"
        )
        every { pluginInfo } returns pluginInformation
        settings = SnykApplicationSettingsStateService()
        clientUnderTest = AdvisorApiClient(server.url("/").toString(), settings)
    }

    @Test
    fun `scoreNpmPackages should return 404 on empty payload`() {
        server.enqueueResponse("scores-npm-packages_empty-payload_404.json", 404)
        val advisorScoreService = clientUnderTest.scoreService()

        val response = advisorScoreService.scoresNpmPackages(listOf()).execute()

        assertEquals(404, response.code())
    }

    @Test
    fun `scoreNpmPackages should return 200 on payload with multiple packages`() {
        server.enqueueResponse("scores-npm-packages_multiple-packages-payload_200.json", 200)
        val expectedPackageInfoLabels = PackageInfoLabels(
            popularity = "Key ecosystem project",
            maintenance = "Healthy",
            community = "Active",
            security = "No known security issues"
        )
        val advisorScoreService = clientUnderTest.scoreService()

        val response = advisorScoreService.scoresNpmPackages(listOf()).execute()

        // asserts
        assertEquals(200, response.code())

        val npmPackagesInfo = response.body()
        assertNotNull(npmPackagesInfo)
        assertEquals(2, npmPackagesInfo?.size)

        val jqueryPackageInfo = npmPackagesInfo?.first()
        assertEquals("jquery", jqueryPackageInfo?.name)
        assertEquals(0.97142857, jqueryPackageInfo?.score)
        assertEquals(false, jqueryPackageInfo?.pending)
        assertNull(jqueryPackageInfo?.error)
        assertEquals(expectedPackageInfoLabels, jqueryPackageInfo?.labels)

        val vuePackageInfo = npmPackagesInfo?.last()
        assertEquals("vue", vuePackageInfo?.name)
        assertEquals(0.9475, vuePackageInfo?.score)
        assertEquals(true, vuePackageInfo?.pending)
        assertNull(vuePackageInfo?.error)
        assertEquals(expectedPackageInfoLabels, vuePackageInfo?.labels)
    }

    @Test
    fun `scoreNpmPackages should contain error with non-existing-package`() {
        server.enqueueResponse("scores-npm-packages_non-existing-package_200.json", 200)
        val advisorScoreService = clientUnderTest.scoreService()

        val response = advisorScoreService.scoresPythonPackages(listOf("non-existing-package")).execute()

        // asserts
        assertEquals(200, response.code())

        val npmPackagesInfo = response.body()
        assertNotNull(npmPackagesInfo)
        assertEquals(1, npmPackagesInfo?.size)

        val nonExistingPackageInfo = npmPackagesInfo?.first()
        assertEquals("non-existing-package", nonExistingPackageInfo?.name)
        assertEquals("not found", nonExistingPackageInfo?.error)
    }

    @Test
    fun `scoresPythonPackages should return 404 on empty payload`() {
        server.enqueueResponse("scores-python-packages_empty-payload_404.json", 404)
        val advisorScoreService = clientUnderTest.scoreService()

        val response = advisorScoreService.scoresPythonPackages(listOf()).execute()

        assertEquals(404, response.code())
    }

    @Test
    fun `scoresPythonPackages should return 200 on payload with multiple packages`() {
        server.enqueueResponse("scores-python-packages_multiple-packages-payload_200.json", 200)
        val expectedPackageInfoLabels = PackageInfoLabels(
            popularity = "Key ecosystem project",
            maintenance = "Sustainable",
            community = "Active",
            security = "No known security issues"
        )
        val advisorScoreService = clientUnderTest.scoreService()

        val response = advisorScoreService.scoresNpmPackages(listOf()).execute()

        // asserts
        assertEquals(200, response.code())

        val pythonPackageInfo = response.body()
        assertNotNull(pythonPackageInfo)
        assertEquals(2, pythonPackageInfo?.size)

        val djangoPackageInfo = pythonPackageInfo?.first()
        assertEquals("django", djangoPackageInfo?.name)
        assertEquals(0.9114285714285715, djangoPackageInfo?.score)
        assertEquals(false, djangoPackageInfo?.pending)
        assertNull(djangoPackageInfo?.error)
        assertEquals(expectedPackageInfoLabels, djangoPackageInfo?.labels)

        val tomlPackageInfo = pythonPackageInfo?.last()
        assertEquals("toml", tomlPackageInfo?.name)
        assertEquals(0.8678571428571429, tomlPackageInfo?.score)
        assertEquals(true, tomlPackageInfo?.pending)
        assertNull(tomlPackageInfo?.error)
        assertEquals(expectedPackageInfoLabels, tomlPackageInfo?.labels)
    }

    @Test
    fun `scoresPythonPackages should contain error with non-existing-package`() {
        server.enqueueResponse("scores-python-packages_non-existing-package_200.json", 200)
        val advisorScoreService = clientUnderTest.scoreService()

        val response = advisorScoreService.scoresPythonPackages(listOf("non-existing-package")).execute()

        // asserts
        assertEquals(200, response.code())

        val pythonPackagesInfo = response.body()
        assertNotNull(pythonPackagesInfo)
        assertEquals(1, pythonPackagesInfo?.size)

        val nonExistingPackageInfo = pythonPackagesInfo?.first()
        assertEquals("non-existing-package", nonExistingPackageInfo?.name)
        assertEquals("not found", nonExistingPackageInfo?.error)
    }

    @Test
    fun `getPackagesInfo should return correct List of PackageInfo`() {
        val expectedJsonFile = "scores-npm-packages_multiple-packages-payload_200.json"

        server.enqueueResponse(expectedJsonFile, 200)

        val expectedInfos = Gson().fromJson(
            javaClass.classLoader.getResource("$FIXTURES_BASE_DIR/$expectedJsonFile")!!.readText(),
            Array<PackageInfo>::class.java
        ).toList()

        val expectedNames = expectedInfos.map { it.name }

        assertInfos(
            expectedInfos,
            clientUnderTest.getPackagesInfo(AdvisorPackageManager.NPM, expectedNames)
        )
    }

    private fun assertInfos(expectedInfos: List<PackageInfo>, actualInfos: List<PackageInfo>?) {
        assertNotNull(actualInfos)
        assertEquals(expectedInfos.size, actualInfos?.size)

        actualInfos?.forEachIndexed { index, actual ->
            assertEquals(expectedInfos[index], actual)
        }
    }
}

internal fun MockWebServer.enqueueResponse(fileName: String, statusCode: Int) {
    val inputStream = javaClass.classLoader?.getResourceAsStream("$FIXTURES_BASE_DIR/$fileName")

    val source = inputStream?.let { inputStream.source().buffer() }
    source?.let {
        enqueue(
            MockResponse()
                .setResponseCode(statusCode)
                .setBody(source.readString(StandardCharsets.UTF_8))
        )
    }
}

private const val FIXTURES_BASE_DIR = "text-fixtures/api-responses/advisor"
