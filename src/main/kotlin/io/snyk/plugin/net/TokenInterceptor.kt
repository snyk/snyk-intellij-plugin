package io.snyk.plugin.net

import com.google.gson.Gson
import io.snyk.plugin.getWhoamiService
import io.snyk.plugin.pluginSettings
import okhttp3.Interceptor
import okhttp3.Response
import snyk.common.needsSnykToken
import snyk.pluginInfo
import java.time.LocalDateTime

class TokenInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()

        val endpoint = chain.request().url.toString()
        val token = pluginSettings().token
        if (needsSnykToken(endpoint) && !token.isNullOrEmpty()) {
            // if token is not a json, then it's an API token, so we check the first char for a brace
            if (!token.startsWith('{')) {
                request.addHeader("Authorization", "token $token")
            } else {
                val bearerToken = unmarshalToken(token)
                val expiry = LocalDateTime.parse(bearerToken.expiry)
                if (expiry.isBefore(LocalDateTime.now().plusMinutes(2))) {
                    getWhoamiService().execute()
                }
                request.addHeader("Authorization", "bearer $bearerToken")
            }
        }
        request.addHeader("Accept", "application/json")
            .addHeader("Content-Type", "application/json")
            .addHeader("User-Agent", "snyk-intellij-plugin/${pluginInfo.integrationVersion}")
            .addHeader("x-snyk-ide", "${pluginInfo.integrationName}-${pluginInfo.integrationVersion}")
        return chain.proceed(request.build())
    }

    private fun unmarshalToken(token: String?): OAuthToken {
        return Gson().fromJson(token, OAuthToken::class.java)
    }
}

data class OAuthToken(
    // AccessToken is the token that authorizes and authenticates
    // the requests.
    val accessToken: String,

    // TokenType is the type of token.
    // The Type method returns either this or "Bearer", the default.
    val tokenType: String = "Bearer",

    // RefreshToken is a token that's used by the application
    // (as opposed to the user) to refresh the access token
    // if it expires.
    val refreshToken: String? = null,

    // Expiry is the optional expiration time of the access token.
    //
    // If zero, TokenSource implementations will reuse the same
    // token forever and RefreshToken or equivalent
    // mechanisms for that TokenSource will not be used.
    val expiry: String? = null,

    // raw optionally contains extra metadata from the server
    // when updating a token.
    val raw: Any? = null
)
