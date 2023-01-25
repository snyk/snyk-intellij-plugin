package snyk.amplitude.api

import com.intellij.openapi.diagnostic.logger
import io.snyk.plugin.net.RetrofitClientFactory
import retrofit2.Retrofit
import java.io.IOException

class AmplitudeExperimentApiClient private constructor(
    private val baseUrl: String,
    private val apiKey: String
) {
    private val log = logger<AmplitudeExperimentApiClient>()
    private lateinit var retrofit: Retrofit
    private lateinit var variantServiceEndpoint: VariantService

    /**
     * Fetches all Amplitude experiments belongs to the user.
     */
    @Suppress("TooGenericExceptionCaught")
    fun allVariants(user: ExperimentUser): Map<String, Variant> {
        if (user.userId.isBlank()) {
            // when userId is empty, the deviceId will be used.
            // it is prefilled as a default during object creation of Experiment User
            log.debug("userId is empty")
        }
        log.debug("Fetch variants for user: $user")

        return try {
            val response = variantService().sdkVardata(user).execute()
            if (!response.isSuccessful) {
                log.warn("Error response: $response")
                return emptyMap()
            }

            val variants = response.body()
            log.debug("Received variants: $variants")
            variants ?: emptyMap()
        } catch (e: IOException) {
            log.warn("Could not fetch variants because of network communication error with amplitude server", e)
            emptyMap()
        } catch (e: Exception) {
            log.warn("Could not fetch variants because of unexpected error", e)
            emptyMap()
        }
    }

    internal fun variantService(): VariantService {
        if (!::variantServiceEndpoint.isInitialized) {
            variantServiceEndpoint = createRetrofitIfNeeded().create(VariantService::class.java)
        }
        return variantServiceEndpoint
    }

    private fun createRetrofitIfNeeded(): Retrofit {
        if (!::retrofit.isInitialized) {
            var interceptors = emptyList<okhttp3.Interceptor>()
            if (apiKey.isNotBlank()) {
                interceptors = listOf(AmplitudeApiKeyInterceptor(apiKey))
            }
            retrofit = RetrofitClientFactory.getInstance().createRetrofit(
                "", // don't leak token to amplitude
                baseUrl,
                false,
                interceptors
            )
        }
        return retrofit
    }

    companion object {
        fun create(
            baseUrl: String = "https://api.lab.amplitude.com/",
            apiKey: String
        ): AmplitudeExperimentApiClient {
            return AmplitudeExperimentApiClient(baseUrl, apiKey)
        }
    }

    object Defaults {
        val FALLBACK_VARIANT: Variant = Variant(null, null)
    }
}
