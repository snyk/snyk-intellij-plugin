package io.snyk.plugin.net

import okhttp3.Interceptor
import okhttp3.Response
import snyk.pluginInfo

class TokenInterceptor(private val token: String) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
        request.addHeader("Authorization", "token $token")
            .addHeader("Accept", "application/json")
            .addHeader("Content-Type", "application/json")
            .addHeader("User-Agent", "snyk-intellij-plugin/${pluginInfo.integrationVersion}")
        return chain.proceed(request.build())
    }
}
