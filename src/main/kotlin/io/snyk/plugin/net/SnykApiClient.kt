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
                    val responseBodyString = response.errorBody()?.string();
                    val errorBody= Gson().fromJson<CliConfigSettingsError?>(responseBodyString, CliConfigSettings::class.java)
                    if (errorBody?.userMessage?.isNotEmpty() == true) {
                        throw ClientException(errorBody.userMessage)
                    }
                    return null
                }
                log.warn("Failed to execute `$apiName` call: ${response.errorBody()?.string()}")
                return executeRequest(apiName, retrofitCall.clone(), retryCounter - 1)
            } else {
                return response.body()
            }
        } catch (t: ClientException) {
            val defaultErrorMsg = "Your org's SAST settings are misconfigured."
            val userMessage = if (t.message.isNullOrEmpty()) defaultErrorMsg else t.message!!
            SnykBalloonNotificationHelper.showError(userMessage, null)
            return null
        } catch (t: Throwable) {
            log.warn("Failed to execute '$apiName' network request: ${t.message}", t)
            return executeRequest(apiName, retrofitCall.clone(), retryCounter - 1)
        }
    }

    companion object {
        private val log = logger<SnykApiClient>()
    }
}

class ClientException(userMessage: String) : RuntimeException(userMessage)
