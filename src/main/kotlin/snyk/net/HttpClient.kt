package snyk.net

import com.intellij.ide.BrowserUtil
import com.intellij.notification.NotificationAction
import io.snyk.plugin.getSSLContext
import io.snyk.plugin.getX509TrustManager
import io.snyk.plugin.ui.SnykBalloonNotificationHelper
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * An HTTP client.
 *
 * An `HttpClient` can be used in API clients based on Retrofit. It can be used
 * to configure per-client state, like: a proxy, an authenticator, etc.
 *
 * Default timeout values:
 * - _connect_ - 30 seconds
 * - _read_ - 60 seconds
 * - _write_ - 60 seconds
 */
class HttpClient(
    var connectTimeout: Long = 30,
    var readTimeout: Long = 60,
    var writeTimeout: Long = 60,
    private var disableSslVerification: Boolean = false,
    var interceptors: List<Interceptor> = listOf()
) {
    companion object {
        const val BALLOON_MESSAGE_ILLEGAL_STATE_EXCEPTION =
            "Could not initialize SSL security to communicate with the Snyk services (%s)."
    }

    fun build(): OkHttpClient {
        try {
            val httpClientBuilder = OkHttpClient.Builder()
                .connectTimeout(connectTimeout, TimeUnit.SECONDS)
                .readTimeout(readTimeout, TimeUnit.SECONDS)
                .writeTimeout(writeTimeout, TimeUnit.SECONDS)
                .sslSocketFactory(getSSLContext().socketFactory, getX509TrustManager())
            httpClientBuilder.interceptors().addAll(interceptors)

            if (disableSslVerification) {
                httpClientBuilder.ignoreAllSslErrors()
            }

            return httpClientBuilder.build()
        } catch (e: IllegalStateException) {
            val message = String.format(BALLOON_MESSAGE_ILLEGAL_STATE_EXCEPTION, e.localizedMessage)
            SnykBalloonNotificationHelper.showError(
                message,
                null,
                NotificationAction.createSimple("Contact support...") {
                    BrowserUtil.browse("https://snyk.io/contact-us/?utm_source=JETBRAINS_IDE")
                })
            throw e
        }
    }
}

private fun OkHttpClient.Builder.ignoreAllSslErrors() {
    val unsafeTrustManager = object : X509TrustManager {
        override fun checkClientTrusted(certs: Array<X509Certificate>, authType: String) = Unit
        override fun checkServerTrusted(certs: Array<X509Certificate>, authType: String) = Unit
        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
    }

    val insecureSocketFactory = SSLContext.getInstance("TLSv1.2").apply {
        val trustAllCertificates = arrayOf<TrustManager>(unsafeTrustManager)
        init(null, trustAllCertificates, SecureRandom())
    }.socketFactory

    sslSocketFactory(insecureSocketFactory, unsafeTrustManager)
    hostnameVerifier(HostnameVerifier { _, _ -> true })
}
