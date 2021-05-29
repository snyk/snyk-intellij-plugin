package io.snyk.plugin.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import io.snyk.plugin.net.SnykApiClient
import io.snyk.plugin.net.TokenInterceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.security.KeyManagementException
import java.security.NoSuchAlgorithmException
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

@Service
class SnykApiService {

    val sastOnServerEnabled: Boolean?
        get() = snykApiClient?.sastOnServerEnabled

    val userId: String?
        get() = snykApiClient?.userId

    private val baseClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private fun createRetrofit(token: String, baseUrl: String, disableSslVerification: Boolean): Retrofit {
        val builder = baseClient.newBuilder()
            .addInterceptor(TokenInterceptor(token))

        if (disableSslVerification) {
            val x509TrustManager = buildUnsafeTrustManager()
            val trustAllCertificates = arrayOf<TrustManager>(x509TrustManager)
            try {
                val sslProtocol = "SSL"
                val sslContext = SSLContext.getInstance(sslProtocol)
                sslContext.init(null, trustAllCertificates, SecureRandom())
                val sslSocketFactory = sslContext.socketFactory
                builder.sslSocketFactory(sslSocketFactory, x509TrustManager)
            } catch (e: NoSuchAlgorithmException) {
                log.error(e)
            } catch (e: KeyManagementException) {
                log.error(e)
            }
        }
        val client = builder.build()

        return Retrofit.Builder()
            .client(client)
            .baseUrl(baseUrl)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    private fun buildUnsafeTrustManager(): X509TrustManager = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
    }

    private val snykApiClient: SnykApiClient?
        get() {
            val appSettings = service<SnykApplicationSettingsStateService>()
            var endpoint = appSettings.customEndpointUrl
            if (endpoint.isNullOrEmpty()) endpoint = "https://snyk.io/api/"

            val retrofit = try {
                createRetrofit(
                    token = appSettings.token ?: "",
                    baseUrl = endpoint,
                    disableSslVerification = appSettings.ignoreUnknownCA
                )
            } catch (e: Exception) {
                log.error("Failed to create Retrofit client for endpoint: $endpoint", e)
                return null
            }
            return SnykApiClient(retrofit)
        }

    companion object {
        private val log = logger<SnykApiService>()
    }
}
