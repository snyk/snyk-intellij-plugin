package snyk.amplitude.api

import com.intellij.openapi.diagnostic.logger
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import snyk.net.HttpClient
import java.io.IOException

class AmplitudeExperimentApiClient private constructor(
    private val baseUrl: String,
    private val httpClient: OkHttpClient
) {
    private val log = logger<AmplitudeExperimentApiClient>()
    private lateinit var retrofit: Retrofit
    private lateinit var variantServiceEndpoint: VariantService

    /**
     * Fetches all Amplitude experiments belongs to the user.
     */
    @Suppress("TooGenericExceptionCaught")
    fun allVariants(user: ExperimentUser): Map<String, Variant> {
        if (user.userId.isBlank() && user.deviceId.isBlank()) {
            log.warn("userId and deviceId are empty; amplitude may not resolve identity")
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
            retrofit = Retrofit.Builder()
                .client(httpClient)
                .baseUrl(baseUrl)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
        }
        return retrofit
    }

    companion object {
        fun create(
            baseUrl: String = "https://api.lab.amplitude.com/",
            apiKey: String,
            httpClient: HttpClient = HttpClient()
        ): AmplitudeExperimentApiClient {
            if (apiKey.isNotBlank()) {
                httpClient.interceptors = httpClient.interceptors.plus(AmplitudeApiKeyInterceptor(apiKey))
            }
            return AmplitudeExperimentApiClient(baseUrl, httpClient.build())
        }
    }

    object Defaults {
        val FALLBACK_VARIANT: Variant = Variant(null, null)
    }
}
