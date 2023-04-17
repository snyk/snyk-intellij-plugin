package io.snyk.plugin.net

import com.intellij.util.net.HttpConfigurable
import okhttp3.Authenticator
import okhttp3.Credentials
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

const val PROXY_AUTHORIZATION_HEADER_NAME = "Proxy-Authorization"

class RetrofitAuthenticator(
    // injectable httpconfigurable for testing
    private var httpConfigurable: HttpConfigurable? = null
) : Authenticator {
    override fun authenticate(route: Route?, response: Response): Request {
        if (httpConfigurable == null) {
            httpConfigurable = HttpConfigurable.getInstance()
        }

        // if no proxy configuration available, we just return the request
        if (httpConfigurable?.PROXY_AUTHENTICATION == false) {
            return response.request
        }

        val prompt = "Snyk: Please enter your proxy credentials for connecting"
        val auth = httpConfigurable?.getPromptedAuthentication(response.request.url.host, prompt)

        if (auth == null || auth.userName.isNullOrBlank() || auth.password == null || auth.password.isEmpty()) {
            return response.request
        }

        val credential = Credentials.basic(auth.userName, String(auth.password))
        return response.request.newBuilder()
            .header(PROXY_AUTHORIZATION_HEADER_NAME, credential)
            .build()
    }
}
