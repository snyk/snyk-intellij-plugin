package io.snyk.plugin.services

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import io.snyk.plugin.net.CliConfigSettings
import io.snyk.plugin.net.FalsePositivePayload
import io.snyk.plugin.net.SnykApiClient
import io.snyk.plugin.net.TokenInterceptor
import io.snyk.plugin.pluginSettings
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
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
class SnykApiService : Disposable {

    val sastSettings: CliConfigSettings?
        get() {
            return getSnykApiClient()?.sastSettings(pluginSettings().organization)
        }

    val userId: String?
        get() = getSnykApiClient()?.getUserId()

    fun reportFalsePositive(payload: FalsePositivePayload): Boolean =
        getSnykApiClient()?.reportFalsePositive(payload) ?: false

    // mostly needed for httpClient correctly shutdown in Tests
    override fun dispose() {
        baseClient.dispatcher.executorService.shutdown()
        baseClient.connectionPool.evictAll()
    }

    private val baseClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    // todo: unify/generalize with ai.deepcode.javaclient.DeepCodeRestApiImpl.buildRetrofit
    private fun createRetrofit(
        token: String,
        baseUrl: String,
        disableSslVerification: Boolean,
        requestLogging: Boolean = false
    ): Retrofit {
        val logging = HttpLoggingInterceptor()
        // set your desired log level
        if (requestLogging) {
            logging.setLevel(HttpLoggingInterceptor.Level.BODY)
        } else {
            logging.setLevel(HttpLoggingInterceptor.Level.NONE)
        }

        val builder = baseClient.newBuilder()
            .addInterceptor(TokenInterceptor(token))
            .addInterceptor(logging)

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

    private var currentUniqSnykApiClient: UniqSnykApiClient? = null

    private fun getSnykApiClient(): SnykApiClient? {
        val appSettings = pluginSettings()
        var endpoint = appSettings.customEndpointUrl
        if (endpoint.isNullOrEmpty()) endpoint = "https://snyk.io/api/"

        val token = appSettings.token ?: ""
        val baseUrl: String = if (endpoint.endsWith('/')) endpoint else "$endpoint/"
        val disableSslVerification = appSettings.ignoreUnknownCA

        if (currentUniqSnykApiClient?.token != token ||
            currentUniqSnykApiClient?.baseUrl != baseUrl ||
            currentUniqSnykApiClient?.disableSslVerification != disableSslVerification
        ) {
            log.debug("Creating new SnykApiClient")
            currentUniqSnykApiClient = try {
                val retrofit = createRetrofit(token, baseUrl, disableSslVerification)
                UniqSnykApiClient(
                    snykApiClient = SnykApiClient(retrofit),
                    token = token,
                    baseUrl = baseUrl,
                    disableSslVerification = disableSslVerification
                )
            } catch (t: Throwable) {
                log.warn("Failed to create Retrofit client for endpoint: $endpoint", t)
                null
            }
        }
        return currentUniqSnykApiClient?.snykApiClient
    }

    private data class UniqSnykApiClient(
        val snykApiClient: SnykApiClient,
        val token: String,
        val baseUrl: String,
        val disableSslVerification: Boolean
    )

    companion object {
        private val log = logger<SnykApiService>()
    }
}
