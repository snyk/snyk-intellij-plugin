package snyk.advisor.api

import com.google.gson.Gson
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.buffer
import okio.source
import org.hamcrest.collection.IsCollectionWithSize.hasSize
import org.hamcrest.core.IsEqual.equalTo
import org.hamcrest.core.IsNull.notNullValue
import org.hamcrest.core.IsNull.nullValue
import org.junit.Assert.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import snyk.advisor.AdvisorPackageManager
import snyk.net.HttpClient
import java.nio.charset.StandardCharsets

class AdvisorApiClientTest {
    @JvmField
    @Rule
    val server: MockWebServer = MockWebServer()

    private lateinit var clientUnderTest: AdvisorApiClient
    private val mockHttpClient: HttpClient = HttpClient().apply {
        connectTimeout = 1
        readTimeout = 1
        writeTimeout = 1
    }

    @Before
    fun setUp() {
        clientUnderTest = AdvisorApiClient.create(
            baseUrl = server.url("/").toString(),
            token = "random-local-test-token",
            httpClient = mockHttpClient
        )!!
    }

    @Test
    fun `scoreNpmPackages should return 404 on empty payload`() {
        server.enqueueResponse("scores-npm-packages_empty-payload_404.json", 404)
        val advisorScoreService = clientUnderTest.scoreService()

        val response = advisorScoreService.scoresNpmPackages(listOf()).execute()

        assertThat(response.code(), equalTo(404))
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
        assertThat(response.code(), equalTo(200))

        val npmPackagesInfo = response.body()
        assertThat(npmPackagesInfo, notNullValue())
        assertThat(npmPackagesInfo, hasSize(2))

        val jqueryPackageInfo = npmPackagesInfo?.first()
        assertThat(jqueryPackageInfo?.name, equalTo("jquery"))
        assertThat(jqueryPackageInfo?.score, equalTo(0.97142857))
        assertThat(jqueryPackageInfo?.pending, equalTo(false))
        assertThat(jqueryPackageInfo?.error, nullValue())
        assertThat(jqueryPackageInfo?.labels, equalTo(expectedPackageInfoLabels))

        val vuePackageInfo = npmPackagesInfo?.last()
        assertThat(vuePackageInfo?.name, equalTo("vue"))
        assertThat(vuePackageInfo?.score, equalTo(0.9475))
        assertThat(vuePackageInfo?.pending, equalTo(true))
        assertThat(vuePackageInfo?.error, nullValue())
        assertThat(vuePackageInfo?.labels, equalTo(expectedPackageInfoLabels))
    }

    @Test
    fun `scoreNpmPackages should contain error with non-existing-package`() {
        server.enqueueResponse("scores-npm-packages_non-existing-package_200.json", 200)
        val advisorScoreService = clientUnderTest.scoreService()

        val response = advisorScoreService.scoresPythonPackages(listOf("non-existing-package")).execute()

        // asserts
        assertThat(response.code(), equalTo(200))

        val npmPackagesInfo = response.body()
        assertThat(npmPackagesInfo, notNullValue())
        assertThat(npmPackagesInfo, hasSize(1))

        val nonExistingPackageInfo = npmPackagesInfo?.first()
        assertThat(nonExistingPackageInfo?.name, equalTo("non-existing-package"))
        assertThat(nonExistingPackageInfo?.error, equalTo("not found"))
    }

    @Test
    fun `scoresPythonPackages should return 404 on empty payload`() {
        server.enqueueResponse("scores-python-packages_empty-payload_404.json", 404)
        val advisorScoreService = clientUnderTest.scoreService()

        val response = advisorScoreService.scoresPythonPackages(listOf()).execute()

        assertThat(response.code(), equalTo(404))
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
        assertThat(response.code(), equalTo(200))

        val pythonPackageInfo = response.body()
        assertThat(pythonPackageInfo, notNullValue())
        assertThat(pythonPackageInfo, hasSize(2))

        val djangoPackageInfo = pythonPackageInfo?.first()
        assertThat(djangoPackageInfo?.name, equalTo("django"))
        assertThat(djangoPackageInfo?.score, equalTo(0.9114285714285715))
        assertThat(djangoPackageInfo?.pending, equalTo(false))
        assertThat(djangoPackageInfo?.error, nullValue())
        assertThat(djangoPackageInfo?.labels, equalTo(expectedPackageInfoLabels))

        val tomlPackageInfo = pythonPackageInfo?.last()
        assertThat(tomlPackageInfo?.name, equalTo("toml"))
        assertThat(tomlPackageInfo?.score, equalTo(0.8678571428571429))
        assertThat(tomlPackageInfo?.pending, equalTo(true))
        assertThat(tomlPackageInfo?.error, nullValue())
        assertThat(tomlPackageInfo?.labels, equalTo(expectedPackageInfoLabels))
    }

    @Test
    fun `scoresPythonPackages should contain error with non-existing-package`() {
        server.enqueueResponse("scores-python-packages_non-existing-package_200.json", 200)
        val advisorScoreService = clientUnderTest.scoreService()

        val response = advisorScoreService.scoresPythonPackages(listOf("non-existing-package")).execute()

        // asserts
        assertThat(response.code(), equalTo(200))

        val pythonPackagesInfo = response.body()
        assertThat(pythonPackagesInfo, notNullValue())
        assertThat(pythonPackagesInfo, hasSize(1))

        val nonExistingPackageInfo = pythonPackagesInfo?.first()
        assertThat(nonExistingPackageInfo?.name, equalTo("non-existing-package"))
        assertThat(nonExistingPackageInfo?.error, equalTo("not found"))
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
        assertThat(actualInfos, notNullValue())
        assertThat(actualInfos, hasSize(expectedInfos.size))

        actualInfos?.forEachIndexed { index, actual ->
            val expected = expectedInfos[index]
            assertThat(actual.name, equalTo(expected.name))
            assertThat(actual.score, equalTo(expected.score))
            assertThat(actual.pending, equalTo(expected.pending))
            assertThat(actual.error, equalTo(expected.error))
            assertThat(actual.labels, equalTo(expected.labels))
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
