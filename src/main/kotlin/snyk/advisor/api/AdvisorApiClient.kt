package snyk.advisor.api

import com.intellij.openapi.diagnostic.logger
import io.snyk.plugin.net.RetrofitClientFactory
import retrofit2.Retrofit
import snyk.advisor.AdvisorPackageManager

class AdvisorApiClient constructor(
    private val baseUrl: String = "https://api.snyk.io/unstable/advisor/"
) {
    private lateinit var retrofit: Retrofit
    private lateinit var scoreServiceEndpoint: ScoreService

    fun getPackagesInfo(packageManager: AdvisorPackageManager, packageNames: List<String>): List<PackageInfo>? {
        try {
            log.debug("Executing request to Advisor api for packages: $packageNames")
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

    internal fun scoreService(): ScoreService {
        if (!::scoreServiceEndpoint.isInitialized) {
            scoreServiceEndpoint = createRetrofitIfNeeded().create(ScoreService::class.java)
        }
        return scoreServiceEndpoint
    }

    private fun createRetrofitIfNeeded(): Retrofit {
        if (!::retrofit.isInitialized) {
            retrofit = RetrofitClientFactory.getInstance().createRetrofit(baseUrl)
        }
        return retrofit
    }

    companion object {
        private val log = logger<AdvisorApiClient>()
    }
}
