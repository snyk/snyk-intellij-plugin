package io.snyk.plugin.net

import com.intellij.openapi.diagnostic.logger
import io.snyk.plugin.pluginSettings
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.apache.http.conn.ssl.NoopHostnameVerifier
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

class RetrofitClientFactory {

    companion object {
        private val log = logger<RetrofitClientFactory>()
        private val instance = RetrofitClientFactory()
        fun getInstance() = instance
    }

    private fun buildUnsafeTrustManager(): X509TrustManager = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
    }

    fun createRetrofit(
        token: String,
        baseUrl: String,
        requestLogging: Boolean = true
    ): Retrofit {
        val logging = HttpLoggingInterceptor()
        // set your desired log level
        if (requestLogging) {
            logging.setLevel(HttpLoggingInterceptor.Level.BODY)
        } else {
            logging.setLevel(HttpLoggingInterceptor.Level.NONE)
        }

        val okHttpClientBuilder = OkHttpClient.Builder()
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .proxyAuthenticator(RetrofitAuthenticator())

        if (pluginSettings().ignoreUnknownCA) {
            val x509TrustManager = buildUnsafeTrustManager()
            val trustAllCertificates = arrayOf<TrustManager>(x509TrustManager)
            try {
                val sslContext = SSLContext.getInstance("TLSv1.2")
                sslContext.init(null, trustAllCertificates, SecureRandom())
                okHttpClientBuilder.sslSocketFactory(sslContext.socketFactory, x509TrustManager)
                okHttpClientBuilder.hostnameVerifier(NoopHostnameVerifier())
            } catch (e: NoSuchAlgorithmException) {
                log.error(e)
            } catch (e: KeyManagementException) {
                log.error(e)
            }
        }
        val client = okHttpClientBuilder
            .addInterceptor(TokenInterceptor(token))
            .addInterceptor(logging)
            .build()

        return Retrofit.Builder()
            .client(client)
            .baseUrl(baseUrl)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
}
