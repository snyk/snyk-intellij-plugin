package snyk.advisor.api

import com.intellij.openapi.diagnostic.logger
import io.snyk.plugin.net.TokenInterceptor
import okhttp3.OkHttpClient
import org.jetbrains.annotations.TestOnly
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import snyk.advisor.AdvisorPackageManager
import snyk.net.HttpClient

class AdvisorApiClient private constructor(
    private val baseUrl: String,
    private val httpClient: OkHttpClient
) {
    private lateinit var retrofit: Retrofit
    private lateinit var scoreServiceEndpoint: ScoreService

    fun getPackagesInfo(packageManager: AdvisorPackageManager, packageNames: List<String>): List<PackageInfo>? {
        try {
            log.debug("Executing request to Advisor api")
            val retrofitCall = when (packageManager) {
                AdvisorPackageManager.NPM -> scoreService().scoresNpmPackages(packages = packageNames)
                AdvisorPackageManager.PYTHON -> scoreService().scoresPythonPackages(packages = packageNames)
            }
            val response = retrofitCall.execute()
            if (!response.isSuccessful) {
                log.warn("Failed to execute Advisor api call: ${response.errorBody()?.string()}")
                return null
            }
            return response.body()
        } catch (t: Throwable) {
            log.warn("Failed to execute Advisor api network request: ${t.message}", t)
            return null
        }
    }

    @TestOnly
    internal fun scoreService(): ScoreService {
        if (!::scoreServiceEndpoint.isInitialized) {
            scoreServiceEndpoint = createRetrofitIfNeeded().create(ScoreService::class.java)
        }
        return scoreServiceEndpoint
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
        private val log = logger<AdvisorApiClient>()
        fun create(
            //TODO(pavel): migrate baseUrl from String to Endpoint object
            baseUrl: String = "https://api.snyk.io/unstable/advisor/",
            token: String,
            httpClient: HttpClient = HttpClient()
        ): AdvisorApiClient? {
            if (token.isNotBlank()) {
                httpClient.interceptors = httpClient.interceptors.plus(TokenInterceptor(token))
            }
            return try {
                AdvisorApiClient(baseUrl, httpClient.build())
            } catch (t: Throwable) {
                log.warn("Failed to create HttpClient: ${t.message}", t)
                return null
            }
        }
    }
}
