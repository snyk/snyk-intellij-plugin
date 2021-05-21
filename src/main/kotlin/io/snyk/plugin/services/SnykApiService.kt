package io.snyk.plugin.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import io.snyk.plugin.net.SnykApiClient
import io.snyk.plugin.net.TokenInterceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

@Service
class SnykApiService {

    val sastOnServerEnabled: Boolean?
        get() = snykApiService.sastOnServerEnabled

    val userId: String?
        get() = snykApiService.userId

    private val baseClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private fun createRetrofit(token: String, baseUrl: String): Retrofit {
        val client = baseClient.newBuilder()
            .addInterceptor(TokenInterceptor(token))
            .build()

        return Retrofit.Builder()
            .client(client)
            .baseUrl(baseUrl)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    private val snykApiService: SnykApiClient
        get() {
            var endpoint = service<SnykApplicationSettingsStateService>().customEndpointUrl
            if (endpoint.isNullOrEmpty()) endpoint = "https://snyk.io/api/"

            val retrofit = createRetrofit(
                token = service<SnykApplicationSettingsStateService>().token ?: "",
                baseUrl = endpoint
            )
            return SnykApiClient(retrofit)
        }


}
