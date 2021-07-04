package snyk.advisor

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.util.Alarm
import io.snyk.plugin.services.SnykApplicationSettingsStateService
import snyk.advisor.api.AdvisorApiClient
import snyk.advisor.api.PackageInfo

@Service
class AdvisorServiceImpl : AdvisorService,  Disposable {
    private val log = logger<AdvisorServiceImpl>()
    private val apiClient by lazy { createAdvisorApiClient() }

    private val alarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, this)

    override fun requestPackageInfos(project: Project?,
                                     packageManager: AdvisorPackageManager,
                                     packageNames: List<String>,
                                     pollingDelay: Int,
                                     onPackageInfoReady: (name: String, PackageInfo?) -> Unit) {
        object : Task.Backgroundable(project, "Retrieving Advisor info", true) {
            override fun run(indicator: ProgressIndicator ) {
                try {
                    log.debug("Executing request to Advisor api")
                    val retrofitCall = when(packageManager) {
                        AdvisorPackageManager.NPM -> apiClient.scoreService().scoresNpmPackages(packages = packageNames)
                        AdvisorPackageManager.PYTHON -> apiClient.scoreService().scoresPythonPackages(packages = packageNames)
                    }
                    val response = retrofitCall.execute()
                    if (!response.isSuccessful) {
                        log.warn("Failed to execute Advisor api call: ${response.errorBody()?.string()}")
                        return
                    }
                    val infos = response.body() ?: return

                    val packagesInfoReady = infos.filter { !it.pending }
                    val packageNamesToPollLater = infos.filter { it.pending }.map { it.name }

                    packagesInfoReady.forEach { onPackageInfoReady(it.name, it) }

                    if (packageNamesToPollLater.isNotEmpty() && pollingDelay < POLLING_THRESHOLD) {
                        alarm.addRequest({
                            requestPackageInfos(
                                project = project,
                                packageManager = packageManager,
                                packageNames = packageNamesToPollLater,
                                pollingDelay = pollingDelay * 2,
                                onPackageInfoReady = onPackageInfoReady
                            )
                        }, pollingDelay * 1000)
                    } else {
                        packageNamesToPollLater.forEach { onPackageInfoReady(it, null) }
                    }

                } catch (t: Throwable) {
                    log.warn("Failed to execute Advisor api network request: ${t.message}", t)
                }
            }
        }.queue()
    }

    private fun createAdvisorApiClient(): AdvisorApiClient {
        //TODO(pavel): customize parameter here + better exception handling
        val token = service<SnykApplicationSettingsStateService>().token
        if (token.isNullOrBlank()) {
            throw IllegalArgumentException("Token cannot be empty")
        }
        return AdvisorApiClient.create(token = token)
    }

    override fun dispose() {}

    companion object {
        private const val POLLING_THRESHOLD = 100 //sec
    }
}
