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
class AdvisorServiceImpl : AdvisorService, Disposable {

    private val alarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, this)

    private val apiClient by lazy {
        //TODO(pavel): customize parameter here
        val token = service<SnykApplicationSettingsStateService>().token
        return@lazy if (token.isNullOrBlank()) {
            log.warn("Token cannot be empty")
            null
        } else {
            AdvisorApiClient.create(token = token)
        }
    }

    override fun requestPackageInfos(project: Project?,
                                     packageManager: AdvisorPackageManager,
                                     packageNames: List<String>,
                                     pollingDelay: Int,
                                     onPackageInfoReady: (name: String, PackageInfo?) -> Unit) {
        object : Task.Backgroundable(project, "Retrieving Advisor info", true) {
            override fun run(indicator: ProgressIndicator) {
                val infos = apiClient?.getPackagesInfo(packageManager, packageNames) ?: return

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
            }
        }.queue()
    }

    override fun dispose() {}

    companion object {
        private val log = logger<AdvisorServiceImpl>()
        private const val POLLING_THRESHOLD = 100 //sec
    }
}
