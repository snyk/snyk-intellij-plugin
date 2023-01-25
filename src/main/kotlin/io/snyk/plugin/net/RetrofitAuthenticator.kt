package io.snyk.plugin.net

import com.intellij.util.net.HttpConfigurable
import okhttp3.Authenticator
import okhttp3.Credentials
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

class RetrofitAuthenticator : Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        val prompt = "Snyk: Please enter your proxy credentials for connecting"
        val auth = HttpConfigurable.getInstance().getPromptedAuthentication(response.request.url.host, prompt)

        if (auth.userName == null || auth.password == null) {
            return response.request
        }

        val credential = Credentials.basic(auth.userName, String(auth.password))
        return response.request.newBuilder()
            .header("Proxy-Authorization", credential)
            .build()
    }
}
