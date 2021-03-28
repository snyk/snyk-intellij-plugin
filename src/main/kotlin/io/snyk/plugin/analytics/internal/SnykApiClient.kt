package io.snyk.plugin.analytics.internal

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Main entrypoint for using Snyk API.
 */
class SnykApiClient private constructor(
    private val token: String,
    private val baseUrl: String
) {
    private lateinit var retrofit: Retrofit
    private lateinit var userServiceEndpoint: UserService

    fun userService(): UserService {
        if (!::userServiceEndpoint.isInitialized) {
            userServiceEndpoint = createRetrofit().create(UserService::class.java)
        }
        return userServiceEndpoint
    }

    private fun createRetrofit(): Retrofit {
        if (!::retrofit.isInitialized) {
            val client = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .addInterceptor(TokenInterceptor(token))
                .build()

            retrofit = Retrofit.Builder()
                .client(client)
                .baseUrl(baseUrl)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
        }
        return retrofit
    }

    companion object {
        fun create(token: String, baseUrl: String = "https://snyk.io/api/"): SnykApiClient {
            return SnykApiClient(token, baseUrl)
        }
    }
}
