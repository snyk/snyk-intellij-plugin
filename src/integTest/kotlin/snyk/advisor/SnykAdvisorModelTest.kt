package snyk.advisor

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.util.Alarm
import org.junit.Test
import snyk.advisor.api.PackageInfo
import snyk.advisor.api.PackageInfoLabels

class SnykAdvisorModelTest : LightPlatformTestCase() {

    @Test
    fun testScoresUpdatedAfterRequest() {
        val model = SnykAdvisorModel().apply {
            setAdvisorService(FakeAdvisorService())
        }
        packageName2Score.keys.forEach {
            val currentScore = model.getScore(null, AdvisorPackageManager.NPM, it)
            assertNull("Score should be not set (NULL) before actual request to Advisor executed", currentScore)
        }

        fun hasPackageWithUnknownScore() = packageName2Score.keys.any {
            model.getScore(null, AdvisorPackageManager.NPM, it) == null
        }

        fun hasPackageWithScore() = packageName2Score.keys.any {
            model.getScore(null, AdvisorPackageManager.NPM, it) != null
        }

        fun hasNoPackagesWithScore() = packageName2Score.keys.none {
            model.getScore(null, AdvisorPackageManager.NPM, it) != null
        }

        waitForRequestsStartCollecting()
        waitForRequestsBatched()
        assertTrue("All scores should NOT be set before actual request to Advisor executed", hasNoPackagesWithScore())

        waitForServerResponse(0.5)
        waitExtraUntil { hasPackageWithScore() }
        assertTrue(
            "Scores should exist for some packages and not exist for others on this stage",
            hasPackageWithScore() && hasPackageWithUnknownScore()
        )

        waitForServerResponse(0.5)
        waitExtraUntil { !hasPackageWithUnknownScore() }

        assertFalse("All scores should be set after actual request to Advisor executed", hasPackageWithUnknownScore())
    }

    @Test
    fun testInfoNotReceived() {
        val model = SnykAdvisorModel().apply {
            setAdvisorService(FakeAdvisorService())
        }
        val fakePackageName = "fake_package"
        var score = model.getScore(null, AdvisorPackageManager.NPM, fakePackageName)
        assertNull("Score should be not set (NULL) before actual request to Advisor executed", score)

        waitForRequestsBatched()
        waitForServerResponse()
        waitExtraUntil { model.getPackagesRequested().isEmpty() }

        score = model.getScore(null, AdvisorPackageManager.NPM, fakePackageName)
        assertNull("Score should stay not set (NULL) for unknown package", score)
    }

    @Test
    fun testRequestsProceedingOrder() {
        val model = SnykAdvisorModel().apply {
            setAdvisorService(FakeAdvisorService())
        }
        val packagesRequestDelayed = model.getPackagesRequestDelayed()
        val packagesRequested = model.getPackagesRequested()
        val packageName1 = "some_package"
        val packageName2 = "other_package"
        val allPackages = setOf(packageName1, packageName2)

        assertTrue(packagesRequestDelayed.isEmpty())
        assertTrue(packagesRequested.isEmpty())

        // first ever requests
        model.getScore(null, AdvisorPackageManager.NPM, packageName1)
        model.getScore(null, AdvisorPackageManager.NPM, packageName2)
        waitForRequestsStartCollecting()

        // requests after SnykAdvisorModel.IGNORE_REQUESTS_DELAY -> should start collecting
        model.getScore(null, AdvisorPackageManager.NPM, packageName1)
        model.getScore(null, AdvisorPackageManager.NPM, packageName2)
        waitExtraUntil { packagesRequestDelayed.size == 2 }
        assertTrue(packagesRequestDelayed.size == 2 && packagesRequestDelayed == allPackages)
        assertTrue(packagesRequested.isEmpty())

        waitForRequestsBatched()
        waitExtraUntil { packagesRequestDelayed.isEmpty() }
        assertTrue(packagesRequestDelayed.isEmpty())
        assertTrue(packagesRequested.size == 2 && packagesRequested == allPackages)

        waitForServerResponse()
        waitExtraUntil { packagesRequested.isEmpty() }
        assertTrue(packagesRequestDelayed.isEmpty())
        assertTrue(packagesRequested.isEmpty())
    }

    private fun waitForRequestsStartCollecting() {
        // wait for batch request collected and executed + time to finish all background jobs
        Thread.sleep(SnykAdvisorModel.IGNORE_REQUESTS_DELAY_MS + 10)
    }

    private fun waitForRequestsBatched() {
        // wait for batch request collected and executed + time to finish all background jobs
        Thread.sleep(SnykAdvisorModel.SCORE_REQUESTS_BATCHING_DELAY_MS.toLong() + 10)
    }

    private fun waitForServerResponse(ratio: Double = 1.0) {
        // wait for fake "server" to produce some(ratio 0.0 ... 1.0) responses + time to finish all background jobs
        Thread.sleep((MAX_RESPONSE_TIME * ratio).toLong() + 10)
    }

    private fun waitExtraUntil(checkCondition: () -> Boolean) {
        // extra 10..1000 ms to finish all background jobs
        var counter: Long = 1
        do {
            Thread.sleep(10)
            counter++
        } while ( !checkCondition() && counter <= 100)
    }

    private class FakeAdvisorService : AdvisorService, Disposable {

        private val alarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, this)

        override fun dispose() {}

        override fun requestPackageInfos(project: Project?,
                                         packageManager: AdvisorPackageManager,
                                         packageNames: List<String>,
                                         pollingDelay: Int,
                                         onPackageInfoReady: (name: String, PackageInfo) -> Unit,
                                         onFailGetInfo: (name: String) -> Unit) {
            packageNames.forEach { name ->
                val score = packageName2Score[name]
                val timeout = score?.let { (MAX_RESPONSE_TIME * score).toLong() }
                    ?: (100..MAX_RESPONSE_TIME).random().toLong()
                alarm.addRequest({
                    if (score != null) {
                        onPackageInfoReady(name, getPackageInfo(name, score))
                    } else {
                        onFailGetInfo(name)
                    }
                }, timeout)
            }
        }

        private fun getPackageInfo(
            name: String,
            score: Double,
            pending: Boolean = false,
            labels: PackageInfoLabels = PackageInfoLabels(
                popularity = "",
                maintenance = "",
                community = "",
                security = ""
            ),
            error: String? = null
        ) = PackageInfo(name, score, pending, labels, error)

    }

    companion object {
        private const val MAX_RESPONSE_TIME = 1_000 //ms

        private val packageName2Score = mapOf(
            "name-10" to 0.1,
            "name-30" to 0.3,
            "name-50" to 0.5,
            "name-70" to 0.7,
            "name-90" to 0.9
        )
    }
}
