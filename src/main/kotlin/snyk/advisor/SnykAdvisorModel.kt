package snyk.advisor

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.util.Alarm
import org.jetbrains.annotations.TestOnly
import snyk.advisor.api.PackageInfo
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

@Service
class SnykAdvisorModel : Disposable {
    private val log = logger<SnykAdvisorModel>()

    private val name2Info = ConcurrentHashMap<String, PackageInfo>()
    private val packages2FirstRequest = ConcurrentHashMap<String, Instant>()
    private val packagesRequestDelayed: MutableSet<String> = ConcurrentHashMap.newKeySet<String>()
    private val packagesRequested: MutableSet<String> = ConcurrentHashMap.newKeySet<String>()

    private var advisorService: AdvisorService = service<AdvisorServiceImpl>()

    private val alarmToRequestScore = Alarm(Alarm.ThreadToUse.POOLED_THREAD, this)
    private val alarmToDropCache = Alarm(Alarm.ThreadToUse.POOLED_THREAD, this)

    private lateinit var dropCacheRunnable: () -> Unit

    init {
        dropCacheRunnable = {
            name2Info.clear()
            alarmToDropCache.addRequest(dropCacheRunnable, H24_IN_MILLISECONDS)
        }
        dropCacheRunnable()
    }

    fun getScore(project: Project?, packageManager: AdvisorPackageManager, packageName: String): Int? {

        name2Info[packageName]?.let { info ->
            return (info.score * 100).toInt()
        }

        /** Ignore requests for that [packageName] during [IGNORE_REQUESTS_DELAY_MS] after first ever request
         * to avoid create server requests while __user is typing__ */
        val firstRequest = packages2FirstRequest.put(packageName, Instant.now()) ?: return null
        if (firstRequest.plusMillis(IGNORE_REQUESTS_DELAY_MS) > Instant.now()) return null

        /** Proceed requests in batch, by waiting for [SCORE_REQUESTS_BATCHING_DELAY_MS] after last request */
        if (packagesRequestDelayed.add(packageName)) {
            alarmToRequestScore.cancelAllRequests()
            alarmToRequestScore.addRequest({
                // exclude packages received already while we been waiting/delaying
                val packagesToRequest = packagesRequestDelayed - packagesRequested - name2Info.keys
                if (packagesRequested.addAll(packagesToRequest)) {
                    log.debug("Requested: $packagesToRequest")
                    advisorService.requestPackageInfos(
                        project = project,
                        packageManager = packageManager,
                        packageNames = packagesToRequest.toList(),
                        onPackageInfoReady = { name, packageInfo ->
                            packagesRequested.remove(name)
                            name2Info[name] = packageInfo
                            log.debug("Advisor info received for $name : $packageInfo")
                        },
                        onFailGetInfo = { name ->
                            packagesRequested.remove(name)
                            log.debug("Failed to get info for $name")
                        }
                    )
                }
                packagesRequestDelayed.clear()
                packages2FirstRequest.clear()
            }, SCORE_REQUESTS_BATCHING_DELAY_MS)
        }
        return null
    }

    // todo(pavel): internal ?
    @TestOnly
    fun setAdvisorService(mockedAdvisorService: AdvisorService) {
        this.advisorService = mockedAdvisorService
    }

    @TestOnly
    fun getPackagesRequestDelayed() = packagesRequestDelayed

    @TestOnly
    fun getPackagesRequested() = packagesRequested

    override fun dispose() {}

    companion object {
        private const val H24_IN_MILLISECONDS = 24 * 60 * 60 * 1000
        const val IGNORE_REQUESTS_DELAY_MS: Long = 1000
        const val SCORE_REQUESTS_BATCHING_DELAY_MS = 1000
    }
}
