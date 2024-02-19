package io.snyk.plugin.net

import com.google.gson.Gson
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import io.snyk.plugin.getSnykCliAuthenticationService
import io.snyk.plugin.getUserAgentString
import io.snyk.plugin.getWhoamiService
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.services.SnykCliAuthenticationService
import junit.framework.TestCase.assertEquals
import okhttp3.HttpUrl
import okhttp3.Interceptor.Chain
import okhttp3.Request
import org.apache.commons.lang3.SystemUtils
import org.junit.After
import org.junit.Before
import org.junit.Test
import snyk.pluginInfo
import snyk.whoami.WhoamiService
import java.time.OffsetDateTime

class TokenInterceptorTest {
    private val projectManager = mockk<ProjectManager>()
    private val tokenInterceptor = TokenInterceptor(projectManager)
    private val chain = mockk<Chain>(relaxed = true)
    private val requestMock = mockk<Request.Builder>(relaxed = true)
    private val whoamiService = mockk<WhoamiService>(relaxed = true)
    private val authenticationService = mockk<SnykCliAuthenticationService>(relaxed = true)

    val project = mockk<Project>(relaxed = true)

    @Before
    fun setUp() {
        unmockkAll()
        clearAllMocks()

        mockkStatic("io.snyk.plugin.UtilsKt")
        mockkStatic("snyk.PluginInformationKt")
        every { pluginInfo } returns mockk(relaxed = true)
        every { pluginInfo.integrationName } returns "Snyk Intellij Plugin"
        every { pluginInfo.integrationVersion } returns "2.4.61"
        every { pluginInfo.integrationEnvironment } returns "IntelliJ IDEA"
        every { pluginInfo.integrationEnvironmentVersion } returns "2020.3.2"

        every { chain.request().newBuilder() } returns requestMock
        every { requestMock.addHeader(any(), any()) } returns requestMock
        every { chain.request().url } returns HttpUrl.Builder().scheme("https").host("app.snykgov.io")
            .addPathSegment("api").build()
        every { projectManager.openProjects } returns arrayOf(project)

        every { getWhoamiService(project) } returns whoamiService
        every { getSnykCliAuthenticationService(project) } returns authenticationService
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `whoami is called when token is expiring`() {
        val token = OAuthToken(
            access_token = "A", refresh_token = "B", expiry = OffsetDateTime.now().minusSeconds(1).toString()
        )

        every { pluginSettings().token } returns Gson().toJson(token)

        tokenInterceptor.intercept(chain)

        verify { requestMock.addHeader(eq("Authorization"), eq("Bearer ${token.access_token}")) }
        verify { requestMock.addHeader(eq("Accept"), eq("application/json")) }
        verify { whoamiService.execute() }
        verify { authenticationService.executeGetConfigApiCommand() }
    }

    @Test
    fun `user agent header is added`() {
        val expectedHeader = getUserAgentString()
        every { pluginSettings().token } returns "abcd"

        tokenInterceptor.intercept(chain)

        verify { requestMock.addHeader("User-Agent", expectedHeader) }
    }

    @Test
    fun `user agent string is correct`() {
        getUserAgentString()

        verify { pluginInfo.integrationName }
        verify { pluginInfo.integrationVersion }
        verify { pluginInfo.integrationEnvironment }
        verify { pluginInfo.integrationEnvironmentVersion }

        val expectedUserAgent =
            """IntelliJ IDEA/2020.3.2 (${SystemUtils.OS_NAME};${SystemUtils.OS_ARCH}) Snyk Intellij Plugin/2.4.61 (IntelliJ IDEA/2020.3.2)"""

        assertEquals(
            expectedUserAgent, getUserAgentString()
        )
    }
}
