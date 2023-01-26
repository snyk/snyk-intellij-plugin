package io.snyk.plugin.net

import io.snyk.plugin.pluginSettings
import okhttp3.Interceptor
import okhttp3.Response
import snyk.common.needsSnykToken
import snyk.pluginInfo

class TokenInterceptor() : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()

        val endpoint = chain.request().url.toString()
        if (needsSnykToken(endpoint) && !pluginSettings().token.isNullOrEmpty()) {
            request.addHeader("Authorization", "token ${pluginSettings().token}")
        }
        request.addHeader("Accept", "application/json")
            .addHeader("Content-Type", "application/json")
            .addHeader("User-Agent", "snyk-intellij-plugin/${pluginInfo.integrationVersion}")
            .addHeader("x-snyk-ide", "${pluginInfo.integrationName}-${pluginInfo.integrationVersion}")
        return chain.proceed(request.build())
    }
}
