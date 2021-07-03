package snyk.advisor.api

import io.snyk.plugin.net.TokenInterceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import snyk.net.HttpClient

class AdvisorApiClient private constructor(
    private val baseUrl: String,
    private val httpClient: OkHttpClient
) {
    private lateinit var retrofit: Retrofit
    private lateinit var scoreServiceEndpoint: ScoreService

    fun scoreService(): ScoreService {
        if (!::scoreServiceEndpoint.isInitialized) {
            scoreServiceEndpoint = createRetrofitIfNeeded().create(ScoreService::class.java)
        }
        return scoreServiceEndpoint
    }

    private fun createRetrofitIfNeeded(): Retrofit {
        if (!::retrofit.isInitialized) {
            retrofit = Retrofit.Builder()
                .client(httpClient)
                .baseUrl(baseUrl)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
        }
        return retrofit
    }

    companion object {
        fun create(
            //TODO(pavel): migrate baseUrl from String to Endpoint object
            baseUrl: String = "https://api.snyk.io/unstable/advisor/",
            token: String,
            httpClient: HttpClient = HttpClient()
        ): AdvisorApiClient {
            if (token.isNotBlank()) {
                httpClient.interceptors = httpClient.interceptors.plus(TokenInterceptor(token))
            }
            return AdvisorApiClient(baseUrl, httpClient.build())
        }
    }
}
