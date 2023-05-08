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
import io.snyk.plugin.getWhoamiService
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.services.SnykCliAuthenticationService
import okhttp3.HttpUrl
import okhttp3.Interceptor.Chain
import okhttp3.Request
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

        every { chain.request().newBuilder() } returns requestMock
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
}
