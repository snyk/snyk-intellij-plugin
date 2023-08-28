package io.snyk.plugin.net

import com.google.gson.Gson
import com.intellij.openapi.diagnostic.logger
import io.snyk.plugin.ui.SnykBalloonNotificationHelper
import retrofit2.Call
import retrofit2.Retrofit

/**
 * Main entrypoint for using Snyk API.
 */
class SnykApiClient(
    retrofit: Retrofit
) {
    private val cliConfigServiceEndpoint: CliConfigService = retrofit.create(CliConfigService::class.java)
    private val userServiceEndpoint: UserService = retrofit.create(UserService::class.java)
    private val reportFalsePositiveEndpoint: ReportFalsePositiveRetrofitService =
        retrofit.create(ReportFalsePositiveRetrofitService::class.java)

    fun getUserId(): String? = executeRequest(UserService.apiName, userServiceEndpoint.userMe())?.id

    fun sastSettings(org: String? = null): CliConfigSettings? =
        executeRequest(CliConfigService.apiName, cliConfigServiceEndpoint.sast(org))

    fun reportFalsePositive(payload: FalsePositivePayload): Boolean =
        executeRequest(ReportFalsePositiveRetrofitService.apiName, reportFalsePositiveEndpoint.report(payload)) != null

    private fun <T> executeRequest(apiName: String, retrofitCall: Call<T>, retryCounter: Int = 2): T? {
        if (retryCounter < 0) return null
        try {
            log.debug("Executing request to $apiName")
            val response = retrofitCall.execute()
            if (!response.isSuccessful) {
                if (response.code() == 422) {
                    val cliConfigSetting = Gson().fromJson<CliConfigSettings?>(response.errorBody()?.string(), CliConfigSettings::class.java)
                    if (cliConfigSetting?.userMessage?.isNotEmpty() == true) {
                        throw ClientException(cliConfigSetting.userMessage)
                    }
                    return null
                }
                log.warn("Failed to execute `$apiName` call: ${response.errorBody()?.string()}")
                return executeRequest(apiName, retrofitCall.clone(), retryCounter - 1)
            } else {
                return response.body()
            }
        } catch (t: Throwable) {
            log.warn("Failed to execute '$apiName' network request: ${t.message}", t)
            if (t is ClientException) {
                val userMessage = if (t.message.isNullOrEmpty()) "Your org's SAST settings are misconfigured." else t.message!!
                SnykBalloonNotificationHelper.showError(userMessage, null)
                return null
            }
            return executeRequest(apiName, retrofitCall.clone(), retryCounter - 1)
        }
    }

    companion object {
        private val log = logger<SnykApiClient>()
    }
}

class ClientException(userMessage: String) : RuntimeException(userMessage)
