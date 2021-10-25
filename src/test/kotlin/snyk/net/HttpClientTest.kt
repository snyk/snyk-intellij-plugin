package snyk.net

import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import io.snyk.plugin.ui.SnykBalloonNotificationHelper
import junit.framework.TestCase.assertFalse
import org.junit.After
import org.junit.Before
import org.junit.Test
import javax.net.ssl.TrustManagerFactory

class HttpClientTest {

    @Before
    fun setUp() {
        clearAllMocks()
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `should use SSLContext with TLSv12 configured`() {
        val client = HttpClient().build()
        assertFalse(client.sslSocketFactory.supportedCipherSuites.contains("TLS_RSA_WITH_DES_CBC_SHA"))
    }

    @Test(expected = IllegalStateException::class)
    fun `should display balloon error message if ssl context cannot be initialized`() {
        mockkStatic(TrustManagerFactory::class)
        val trustManagerFactory = mockk<TrustManagerFactory>(relaxed = true)
        every { TrustManagerFactory.getInstance(any()) } returns trustManagerFactory
        val exception = IllegalStateException("Test exception. Don't panic")
        every { trustManagerFactory.trustManagers } throws exception

        mockkObject(SnykBalloonNotificationHelper)
        justRun { SnykBalloonNotificationHelper.showError(any(), null, any()) }

        try {
            HttpClient().build()
        } finally {
            val balloonMessage =
                String.format(HttpClient.BALLOON_MESSAGE_ILLEGAL_STATE_EXCEPTION, exception.localizedMessage)
            verify(exactly = 1) { SnykBalloonNotificationHelper.showError(balloonMessage, null, any()) }
        }
    }
}
