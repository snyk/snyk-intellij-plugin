package io.snyk.plugin.services.download

import com.intellij.util.io.HttpRequests
import com.intellij.util.io.RequestBuilder
import io.snyk.plugin.pluginSettings
import org.apache.http.conn.ssl.NoopHostnameVerifier

object HttpRequestHelper {
  fun createRequest(url: String): RequestBuilder {
    val request = HttpRequests.request(url).productNameAsUserAgent().forceHttps(true)
    if (pluginSettings().ignoreUnknownCA) {
      request.hostNameVerifier(NoopHostnameVerifier())
    }
    return request
  }
}
