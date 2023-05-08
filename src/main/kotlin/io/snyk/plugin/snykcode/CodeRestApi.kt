package io.snyk.plugin.snykcode

import ai.deepcode.javaclient.DeepCodeRestApi
import ai.deepcode.javaclient.DeepCodeRestApiImpl
import ai.deepcode.javaclient.core.Base64EncodeRequestInterceptor
import com.intellij.openapi.diagnostic.Logger
import io.snyk.plugin.net.RetrofitClientFactory
import io.snyk.plugin.snykcode.core.SCLogger
import snyk.common.getEndpointUrl
import snyk.common.toSnykCodeApiUrl

var codeRestApi: DeepCodeRestApi? = null
    get() {
        if (field == null)
            codeRestApi = newCodeRestApi()
        return field
    }

fun newCodeRestApi(
    endpoint: String = toSnykCodeApiUrl(getEndpointUrl())
): DeepCodeRestApi {
    val requestLogger = Logger.getInstance(SCLogger.presentableName + "RequestLogging").isDebugEnabled
    val additionalInterceptors = listOf(Base64EncodeRequestInterceptor())
    val retrofit = RetrofitClientFactory.getInstance().createRetrofit(
        endpoint,
        requestLogger,
        additionalInterceptors
    )
    return DeepCodeRestApiImpl(retrofit)
}
