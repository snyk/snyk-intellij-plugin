package snyk.advisor

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import io.snyk.plugin.services.SnykApplicationSettingsStateService
import snyk.advisor.api.AdvisorApiClient
import snyk.advisor.api.PackageInfo

@Service
class AdvisorService {
    private val log = logger<AdvisorService>()
    private val apiClient by lazy { createAdvisorApiClient() }

    fun requestPackageInfos(packageNames: List<String>, packageInfo: (PackageInfo) -> Unit) {
        // example call
        apiClient.scoreService().scoresNpmPackages(packages = packageNames)
    }

    private fun createAdvisorApiClient(): AdvisorApiClient {
        //TODO(pavel): customize parameter here + better exception handling
        val token = service<SnykApplicationSettingsStateService>().token
        if (token.isNullOrBlank()) {
            throw IllegalArgumentException("Token cannot be empty")
        }
        return AdvisorApiClient.create(token = token)
    }
}
