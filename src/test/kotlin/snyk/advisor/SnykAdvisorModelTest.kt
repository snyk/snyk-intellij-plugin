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
        val model = SnykAdvisorModel()
        model.setAdvisorService(FakeAdvisorService())

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

        // wait for batch request collected and executed
        Thread.sleep(SnykAdvisorModel.SCORE_REQUESTS_BATCHING_DELAY.toLong())
        assertTrue("All scores should NOT be set before actual request to Advisor executed", hasNoPackagesWithScore())

        // wait for fake "server" to produce some (~half) responses
        Thread.sleep((MAX_RESPONSE_TIME / 2).toLong())
        assertTrue(
            "Scores should exist for some packages and not exist for others on this stage",
            hasPackageWithScore() && hasPackageWithUnknownScore()
        )

        // wait for fake "server" to produce all responses
        Thread.sleep((MAX_RESPONSE_TIME / 2).toLong())


        // extra 100..1000 ms to finish all background jobs
        var counter: Long = 1
        while (hasPackageWithUnknownScore() && counter < 5) {
            Thread.sleep(counter * 100)
            counter++
        }

        assertFalse("All scores should be set after actual request to Advisor executed", hasPackageWithUnknownScore())
    }


    class FakeAdvisorService : AdvisorService, Disposable {

        private val alarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, this)

        override fun dispose() {}

        override fun requestPackageInfos(project: Project?,
                                         packageManager: AdvisorPackageManager,
                                         packageNames: List<String>,
                                         pollingDelay: Int,
                                         onPackageInfoReady: (name: String, PackageInfo?) -> Unit) {
            packageNames.forEach { name ->
                val score = packageName2Score[name] ?: (0..100).random().toDouble() / 100
                alarm.addRequest({
                    onPackageInfoReady(name, getPackageInfo(name, score))
                }, (MAX_RESPONSE_TIME * score).toLong())
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
