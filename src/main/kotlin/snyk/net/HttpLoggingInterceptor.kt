package snyk.net

import com.intellij.openapi.diagnostic.Logger
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.internal.closeQuietly
import okio.Buffer

class HttpLoggingInterceptor(private val log: Logger) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        val buffer = Buffer()
        val requestBody = request.body
        requestBody?.writeTo(buffer)
        log.warn("--> ${request.method} ${request.url}, payload=${buffer.readUtf8()}")

        val response = chain.proceed(request)
        val responseBody = response.body
        val responseStr = responseBody?.string()
        var responseBodyStr = ""
        if (responseStr != null) {
            if (responseStr.isNotEmpty()) {
                responseBodyStr = responseStr.take(2000)
            }
        }
        log.warn("<-- HTTP Response: code=${response.code}, message=${response.message}, body=${responseBodyStr}")
        responseBody?.closeQuietly()

        return chain.proceed(request)
    }
}
