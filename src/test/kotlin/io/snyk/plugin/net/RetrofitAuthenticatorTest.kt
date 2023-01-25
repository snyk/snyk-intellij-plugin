package io.snyk.plugin.net

import com.intellij.util.Base64
import com.intellij.util.net.HttpConfigurable
import io.mockk.every
import io.mockk.mockk
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import okhttp3.Response
import okhttp3.Route
import org.junit.Test
import java.net.PasswordAuthentication

class RetrofitAuthenticatorTest {

    @Test
    fun authenticate() {
        val httpConfigurableMock = mockk<HttpConfigurable>()
        val auth = mockk<PasswordAuthentication>()
        every { httpConfigurableMock.getPromptedAuthentication(any(), any()) } returns auth
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

        val authHeader = request!!.headers[PROXY_AUTHORIZATION_HEADER_NAME]
        assertNotNull(authHeader)
        assertEquals("Basic dXNlcm5hbWU6cHc=", authHeader)
        assertEquals("dXNlcm5hbWU6cHc=", Base64.encode("username:pw".toByteArray()))
    }
}
