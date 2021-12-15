package snyk.container

import com.google.gson.Gson
import com.intellij.testFramework.LightPlatform4TestCase
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.verify
import io.snyk.plugin.cli.ConsoleCommandRunner
import io.snyk.plugin.removeDummyCliFile
import io.snyk.plugin.setupDummyCliFile
import org.junit.Test
import snyk.container.TestYamls.podYaml

@Suppress("FunctionName")
class ContainerServiceIntegTest : LightPlatform4TestCase() {
    private lateinit var cut: ContainerService
    private val containerResultWithRemediationJson = javaClass.classLoader
        .getResource(("container-test-result-with-remediation.json"))!!.readText(Charsets.UTF_8)
    private val containerResultJson = javaClass.classLoader
        .getResource(("container-test-result.json"))!!.readText(Charsets.UTF_8)

    override fun setUp() {
        super.setUp()
        unmockkAll()
        setupDummyCliFile()
        cut = ContainerService(project)
    }

    override fun tearDown() {
        unmockkAll()
        removeDummyCliFile()
        super.tearDown()
    }

    @Test
    fun `test scan should take all images from KubernetesImageCache and scan them using the CLI`() {
        val (expectedResult, containerResult) = executeScan(containerResultWithRemediationJson)

        val actualCliIssues = containerResult.allCliIssues!!
        assertTrue(actualCliIssues.isNotEmpty())
        val first = actualCliIssues.first()
        assertTrue("BaseRemeditation should not be null", null != first.baseImageRemediationInfo)
        val baseImageRemediationInfo = first.baseImageRemediationInfo!!
        assertTrue(
            "Should have found a minor upgrade remediation",
            baseImageRemediationInfo.minorUpgrades != null
        )
        assertTrue(baseImageRemediationInfo.majorUpgrades == null)
        assertEquals(expectedResult.allCliIssues!!.first().imageName, first.imageName)
        assertEquals(expectedResult.allCliIssues!!.first().imageName, baseImageRemediationInfo.currentImage!!.name)
        assertEquals(8, first.workloadImage!!.lineNumber)
    }

    @Test
    fun `test scan should take all images from KubernetesImageCache and scan them using the CLI, no remediation`() {
        val (expectedResult, containerResult) = executeScan(containerResultJson)

        val actualCliIssues = containerResult.allCliIssues!!
        assertTrue(actualCliIssues.isNotEmpty())
        val first = actualCliIssues.first()
        assertTrue("BaseRemeditation should be null", null == first.baseImageRemediationInfo)
        assertEquals(expectedResult.allCliIssues!!.first().imageName, first.imageName)
    }

    private fun executeScan(expectedResult: String): Pair<ContainerResult, ContainerResult> {
        val cache = setupCacheAndFile()

        // create CLI mock
        val mockkRunner = mockk<ConsoleCommandRunner>()
        every { mockkRunner.execute(any(), any(), any(), project) } returns expectedResult
        cut.setConsoleCommandRunner(mockkRunner)

        val expectedContainerResult =
            ContainerResult(listOf(Gson().fromJson(expectedResult, ContainerIssuesForImage::class.java)), null)

        val scanResult = cut.scan()

        verify { cache.getKubernetesWorkloadImages() }
        return Pair(expectedContainerResult, scanResult)
    }

    private fun setupCacheAndFile(): KubernetesImageCache {
        val fileName = "my-test-pod.yaml"
        val file = createFile(fileName, podYaml())
        val cache = spyk(KubernetesImageCache(project))
        cut.setKubernetesImageCache(cache)
        cache.extractFromFile(file.virtualFile)
        return cache
    }
}
