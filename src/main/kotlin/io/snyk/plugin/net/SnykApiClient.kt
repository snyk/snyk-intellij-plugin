package io.snyk.plugin.net

import com.intellij.openapi.diagnostic.logger
import retrofit2.Call
import retrofit2.Retrofit

/**
 * Main entrypoint for using Snyk API.
 */
class SnykApiClient constructor(
    private val retrofit: Retrofit
) {
    val sastOnServerEnabled: Boolean?
        get() = executeRequest(CliConfigService.apiName, cliConfigService().sast())?.sastEnabled

    val userId: String?
        get() = executeRequest(UserService.apiName, userService().userMe())?.id

    private lateinit var cliConfigServiceEndpoint: CliConfigService
    private lateinit var userServiceEndpoint: UserService

    private fun cliConfigService(): CliConfigService {
        if (!::cliConfigServiceEndpoint.isInitialized) {
            cliConfigServiceEndpoint = retrofit.create(CliConfigService::class.java)
        }
        return cliConfigServiceEndpoint
    }

    private fun userService(): UserService {
        if (!::userServiceEndpoint.isInitialized) {
            userServiceEndpoint = retrofit.create(UserService::class.java)
        }
        return userServiceEndpoint
    }
    private fun <T> executeRequest(apiName: String, retrofitCall: Call<T>): T? = try {
        log.debug("Executing request to $apiName")
        val response = retrofitCall.execute()
        if (!response.isSuccessful) {
            log.warn("Failed to execute `$apiName` call: ${response.errorBody()?.string()}")
        }
        response.body()
    } catch (t: Throwable) {
        log.warn("Failed to execute '$apiName' network request: ${t.message}", t)
        null
    }

    companion object {
        private val log = logger<SnykApiClient>()
    }
}
