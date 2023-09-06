package io.snyk.plugin.net

import com.google.gson.Gson
import com.intellij.openapi.project.ProjectManager
import io.snyk.plugin.getSnykCliAuthenticationService
import io.snyk.plugin.getWhoamiService
import io.snyk.plugin.pluginSettings
import okhttp3.Interceptor
import okhttp3.Response
import org.apache.commons.lang.SystemUtils
import snyk.common.needsSnykToken
import snyk.pluginInfo
import java.time.OffsetDateTime

class TokenInterceptor(private var projectManager: ProjectManager? = null) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
        val endpoint = chain.request().url.toString()

        if (needsSnykToken(endpoint)) {
        // this is temporary. `app.local.host` is not an unknown host be we need to add headers to it
//        if (true) {
            val token = pluginSettings().token ?: return chain.proceed(request.build())

            val oldSnykCodeHeaderName = "Session-Token"
            val authorizationHeaderName = "Authorization"
            request.removeHeader(oldSnykCodeHeaderName)
            if (!token.startsWith('{')) {
                request.addHeader(oldSnykCodeHeaderName, token)
                request.addHeader(authorizationHeaderName, "token $token")
            } else {
                if (projectManager == null) {
                    projectManager = ProjectManager.getInstance()
                }
                val project = projectManager?.openProjects!!.firstOrNull()
                val oAuthToken = Gson().fromJson(token, OAuthToken::class.java)
                val expiry = OffsetDateTime.parse(oAuthToken.expiry)
                if (expiry.isBefore(OffsetDateTime.now().plusMinutes(2))) {
                    getWhoamiService(project)?.execute()
                    getSnykCliAuthenticationService(project)?.executeGetConfigApiCommand()
                }
                request.addHeader(authorizationHeaderName, "Bearer ${oAuthToken.access_token}")
                request.addHeader(oldSnykCodeHeaderName, "Bearer ${oAuthToken.access_token}")
            }
        }
        request.addHeader("Accept", "application/json")
            .addHeader("Content-Type", "application/json")
            .addHeader("User-Agent", getUserAgentString())
            .addHeader("x-snyk-ide", "${pluginInfo.integrationName}-${pluginInfo.integrationVersion}")
        return chain.proceed(request.build())
    }

    fun getUserAgentString(): String {
//      $APPLICATION/$APPLICATION_VERSION ($GOOS;$GOARCH[;$BINARY_NAME]) [$SNYK_INTEGRATION_NAME/$SNYK_INTEGRATION_VERSION [($SNYK_INTEGRATION_ENVIRONMENT/$SNYK_INTEGRATION_ENVIRONMENT_VERSION)]]
        val integrationName = pluginInfo.integrationName
        val integrationVersion = pluginInfo.integrationVersion
        val integrationEnvironment = pluginInfo.integrationEnvironment
        val integrationEnvironmentVersion = pluginInfo.integrationEnvironmentVersion
        val os = SystemUtils.OS_NAME
        val arch = SystemUtils.OS_ARCH

        return "$integrationEnvironment/$integrationEnvironmentVersion " +
            "($os;$arch) $integrationName/$integrationVersion " +
            "($integrationEnvironment/$integrationEnvironmentVersion)"
    }
}

@Suppress("PropertyName")
data class OAuthToken(
    // AccessToken is the token that authorizes and authenticates
    // the requests.
    val access_token: String,

    // TokenType is the type of token.
    // The Type method returns either this or "Bearer", the default.
    val token_type: String = "Bearer",

    // RefreshToken is a token that's used by the application
    // (as opposed to the user) to refresh the access token
    // if it expires.
    val refresh_token: String? = null,

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
