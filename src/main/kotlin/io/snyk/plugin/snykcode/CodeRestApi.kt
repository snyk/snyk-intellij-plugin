package io.snyk.plugin.snykcode

import ai.deepcode.javaclient.DeepCodeRestApiImpl
import ai.deepcode.javaclient.core.Base64EncodeRequestInterceptor
import com.intellij.openapi.diagnostic.Logger
import io.snyk.plugin.net.RetrofitClientFactory
import io.snyk.plugin.snykcode.core.SCLogger
import snyk.common.getEndpointUrl
import snyk.common.toSnykCodeApiUrl

val codeRestApi =
    DeepCodeRestApiImpl(
        RetrofitClientFactory.getInstance().createRetrofit(
            toSnykCodeApiUrl(getEndpointUrl()),
            Logger.getInstance(SCLogger.presentableName + "RequestLogging").isDebugEnabled,
            listOf(Base64EncodeRequestInterceptor())
        )
    )

