package snyk.net

import junit.framework.TestCase.assertFalse
import org.junit.Test

class HttpClientTest {

    @Test
    fun `should use SSLContext with TLSv12 configured`() {
        val client = HttpClient().build()
        assertFalse(client.sslSocketFactory.supportedCipherSuites.contains("TLS_RSA_WITH_DES_CBC_SHA"))
    }
}
