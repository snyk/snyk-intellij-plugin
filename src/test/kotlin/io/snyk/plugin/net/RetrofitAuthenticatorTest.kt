package io.snyk.plugin.net

import com.intellij.util.net.HttpConfigurable
import io.mockk.every
import io.mockk.mockk
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertNull
import okhttp3.Response
import okhttp3.Route
import org.junit.Test
import java.net.PasswordAuthentication
import java.util.Base64

class RetrofitAuthenticatorTest {

    @Test
    fun authenticate() {
        val httpConfigurableMock = mockk<HttpConfigurable>()
        val auth = mockk<PasswordAuthentication>()
        every { httpConfigurableMock.getPromptedAuthentication(any(), any()) } returns auth
        httpConfigurableMock.PROXY_AUTHENTICATION = true
        every { auth.userName } returns "username"
        every { auth.password } returns "pw".toCharArray()
        val cut = RetrofitAuthenticator(httpConfigurableMock)

        val route: Route = mockk()
        val response: Response = Response.Builder()
            .request(okhttp3.Request.Builder().url("https://example.com").build())
            .code(407)
            .protocol(okhttp3.Protocol.HTTP_1_1)
            .message("Proxy Authentication Required")
            .build()

        val request = cut.authenticate(route, response)

        val authHeader = request.headers[PROXY_AUTHORIZATION_HEADER_NAME]
        assertNotNull(authHeader)
        assertEquals("Basic dXNlcm5hbWU6cHc=", authHeader)
        assertEquals("dXNlcm5hbWU6cHc=", String(Base64.getEncoder().encode("username:pw".toByteArray())))
    }

    @Test
    fun `No authorization header added for non-authenticated proxy`() {
        val httpConfigurableMock = mockk<HttpConfigurable>()
        httpConfigurableMock.PROXY_AUTHENTICATION = false
        val cut = RetrofitAuthenticator(httpConfigurableMock)

        val route: Route = mockk()
        val response: Response = Response.Builder()
            .request(okhttp3.Request.Builder().url("https://example.com").build())
            .code(200)
            .protocol(okhttp3.Protocol.HTTP_1_1)
            .message("")
            .build()

        val request = cut.authenticate(route, response)

        val authHeader = request.headers[PROXY_AUTHORIZATION_HEADER_NAME]
        assertNull(authHeader)
    }
}
