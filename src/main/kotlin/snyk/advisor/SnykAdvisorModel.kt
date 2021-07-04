package snyk.advisor

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.util.Alarm
import org.jetbrains.annotations.TestOnly
import snyk.advisor.api.PackageInfo
import java.util.concurrent.ConcurrentHashMap

@Service
class SnykAdvisorModel : Disposable {
    private val log = logger<SnykAdvisorModel>()

    private val package2score = ConcurrentHashMap<String, PackageInfo>()
    private val packagesRequestDelayed: MutableSet<String> = ConcurrentHashMap.newKeySet<String>()
    private val packagesRequested: MutableSet<String> = ConcurrentHashMap.newKeySet<String>()

    private var advisorService: AdvisorService = service<AdvisorServiceImpl>()

    private val alarmToRequestScore = Alarm(Alarm.ThreadToUse.POOLED_THREAD, this)
    private val alarmToDropCache = Alarm(Alarm.ThreadToUse.POOLED_THREAD, this)

    private lateinit var dropCacheRunnable: () -> Unit

    init {
        dropCacheRunnable = {
            package2score.clear()
            alarmToDropCache.addRequest(dropCacheRunnable, H24_IN_MILLISECONDS)
        }
        dropCacheRunnable()
    }

    fun getScore(project: Project?, packageManager: AdvisorPackageManager, packageName: String): Int? {
        val score = package2score[packageName]?.let { (it.score * 100).toInt() }
        if (score == null && packagesRequestDelayed.add(packageName)) {
            /** Proceed requests in batch, by waiting for [SCORE_REQUESTS_BATCHING_DELAY] after last request */
            alarmToRequestScore.cancelAllRequests()
            alarmToRequestScore.addRequest({
                val packagesToRequest = packagesRequestDelayed - packagesRequested - package2score.keys
                if (packagesRequested.addAll(packagesToRequest)) {
                    log.debug("Requested: $packagesToRequest")
                    advisorService.requestPackageInfos(
                        project = project,
                        packageManager = packageManager,
                        packageNames = packagesToRequest.toList()
                    ) { name, packageInfo ->
                        packagesRequested.remove(name)
                        packageInfo?.let {
                            package2score[name] = it
                        }
                        log.debug("Advisor info received for $name : $packageInfo")
                    }
                }
                packagesRequestDelayed.clear()
            }, SCORE_REQUESTS_BATCHING_DELAY)
        }
        return score
    }

    @TestOnly
    fun setAdvisorService(mockedAdvisorService: AdvisorService) {
        this.advisorService = mockedAdvisorService
    }

    override fun dispose() {}

    companion object {
        private const val H24_IN_MILLISECONDS = 24 * 60 * 60 * 1000
        const val SCORE_REQUESTS_BATCHING_DELAY = 1000 //ms
    }
}
