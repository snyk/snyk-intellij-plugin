package snyk.net

import com.intellij.openapi.diagnostic.Logger
import okhttp3.Interceptor
import okhttp3.Response
import okio.Buffer

class HttpLoggingInterceptor(private val log: Logger) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        val buffer = Buffer()
        val requestBody = request.body
        requestBody?.writeTo(buffer)
        log.warn("Request: method=${request.method}, url=${request.url}, body=${buffer.readUtf8()}")

        val response = chain.proceed(request)
        val body = response.body?.source()?.buffer?.clone()?.readUtf8()
        log.warn("Response: code=${response.code}, message=${response.message}, body=${body}")

        return response
    }
}
