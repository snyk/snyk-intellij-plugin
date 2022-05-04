package io.snyk.plugin.net

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.buffer
import okio.source
import java.nio.charset.StandardCharsets

internal fun MockWebServer.enqueueResponse(fileName: String, statusCode: Int, callback: () -> Unit = {}) {
    val inputStream = javaClass.classLoader?.getResourceAsStream("test-fixtures/api-responses/snyk/$fileName")

    val source = inputStream?.let { inputStream.source().buffer() }
    source?.let {
        enqueue(
            MockResponse()
                .setResponseCode(statusCode)
                .setBody(source.readString(StandardCharsets.UTF_8))
                .apply { callback.invoke() }
        )
    }
}
