package snyk.advisor

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.util.Alarm
import snyk.advisor.api.AdvisorApiClient
import snyk.advisor.api.PackageInfo

@Service
class AdvisorServiceImpl : AdvisorService, Disposable {

    private val alarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, this)

    private fun getApiClient(): AdvisorApiClient? {
        return AdvisorApiClient()
    }
    override fun requestPackageInfos(project: Project?,
                                     packageManager: AdvisorPackageManager,
                                     packageNames: List<String>,
                                     pollingDelay: Int,
                                     onPackageInfoReady: (name: String, PackageInfo) -> Unit,
                                     onFailGetInfo: (name: String) -> Unit) {
        object : Task.Backgroundable(project, "Retrieving Advisor info", true) {
            override fun run(indicator: ProgressIndicator) {
                val infos = getApiClient()?.getPackagesInfo(packageManager, packageNames)
                if (infos == null) {
                    packageNames.forEach { onFailGetInfo(it) }
                    return
                }

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
                            onPackageInfoReady = onPackageInfoReady,
                            onFailGetInfo = onFailGetInfo
                        )
                    }, pollingDelay * 1000)
                } else {
                    packageNamesToPollLater.forEach { onFailGetInfo(it) }
                }
            }
        }.queue()
    }

    override fun dispose() {}

    companion object {
        private const val POLLING_THRESHOLD = 100 //sec
    }
}
