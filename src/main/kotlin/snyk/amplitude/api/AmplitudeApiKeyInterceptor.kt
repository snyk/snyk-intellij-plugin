package snyk.amplitude.api

import okhttp3.Interceptor
import okhttp3.Response

class AmplitudeApiKeyInterceptor(private val apiKey: String) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
        request.addHeader("Authorization", "Api-Key $apiKey")
            .addHeader("Accept", "application/json")
            .addHeader("Content-Type", "application/json")
        return chain.proceed(request.build())
    }
}
