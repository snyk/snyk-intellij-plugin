package snyk.advisor

import com.intellij.openapi.project.Project
import snyk.advisor.api.PackageInfo

interface AdvisorService {

    fun requestPackageInfos(project: Project?,
                            packageManager: AdvisorPackageManager,
                            packageNames: List<String>,
                            pollingDelay: Int = 2, //initial delay in sec
                            onPackageInfoReady: (name: String, PackageInfo?) -> Unit)

}
