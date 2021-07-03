package snyk.advisor

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.util.Alarm
import snyk.advisor.api.PackageInfo
import java.util.concurrent.ConcurrentHashMap

@Service
class SnykAdvisorModel : Disposable {
    private val log = logger<SnykAdvisorModel>()

    private val package2score = ConcurrentHashMap<String, PackageInfo>()
    private val packagesRequestDelayed: MutableSet<String> = ConcurrentHashMap.newKeySet<String>()
    private val packagesRequested: MutableSet<String> = ConcurrentHashMap.newKeySet<String>()

    private var advisorService = service<AdvisorService>()

    private val alarmToRequestScore = Alarm(Alarm.ThreadToUse.POOLED_THREAD, this)
    private val alarmToDropCache = Alarm(Alarm.ThreadToUse.POOLED_THREAD, this)

    private lateinit var dropCacheRunnable: () -> Unit

    init {
        dropCacheRunnable = {
            package2score.clear()
            alarmToDropCache.addRequest(dropCacheRunnable, _24H_IN_MILISECONDS)
        }
        dropCacheRunnable()
    }

    fun getScore(project: Project, packageName: String): Int? {
        val score = package2score[packageName]?.let { (it.score * 100).toInt() }
        if (score == null && packagesRequestDelayed.add(packageName)) {
            // Proceed requests in batch every 1 sec
            alarmToRequestScore.cancelAllRequests()
            alarmToRequestScore.addRequest({
                val packagesToRequest = packagesRequestDelayed - packagesRequested - package2score.keys
                if (packagesRequested.addAll(packagesToRequest)) {
                    log.debug("Requested: $packagesToRequest")
                    advisorService.requestPackageInfos(project, packagesToRequest.toList()) {name, packageInfo ->
                        packagesRequested.remove(name)
                        packageInfo?.let {
                            package2score[name] = it
                        }
                        log.debug("Advisor info received for $name : $packageInfo")
                    }
                }
                packagesRequestDelayed.clear()
            }, 1000)
        }
        return score
    }

    // for tests purposes mainly
    fun setAdvisorService(newAdvisorService: AdvisorService) {
        this.advisorService = newAdvisorService
    }

    override fun dispose() {}

    companion object {
        private const val _24H_IN_MILISECONDS = 24 * 60 * 60 * 1000
    }
}
