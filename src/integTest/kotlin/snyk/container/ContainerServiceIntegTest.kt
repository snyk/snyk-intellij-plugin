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
        .getResource(("container-test-results/nginx-with-remediation.json"))!!.readText(Charsets.UTF_8)
    private val containerResultJson = javaClass.classLoader
        .getResource(("container-test-results/nginx-no-remediation.json"))!!.readText(Charsets.UTF_8)
    private val containerResultForFewImagesJson = javaClass.classLoader
        .getResource(("container-test-results/debian-nginx_critical_only.json"))!!.readText(Charsets.UTF_8)

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
    fun `take image from KubernetesImageCache and scan it using the CLI`() {
        val (expectedResult, containerResult) = executeScan(containerResultWithRemediationJson)

        val actualCliIssues = containerResult.allCliIssues!!
        assertTrue(actualCliIssues.isNotEmpty())
        val actualFirstImage = actualCliIssues.first()
        assertTrue("BaseRemeditation should not be null", null != actualFirstImage.baseImageRemediationInfo)
        val actualRemediation = actualFirstImage.baseImageRemediationInfo!!
        assertTrue(
            "Should have found a minor upgrade remediation",
            actualRemediation.minorUpgrades != null
        )
        assertTrue(actualRemediation.majorUpgrades == null)
        val expectedImageName = expectedResult.allCliIssues!!.first().imageName
        assertEquals(expectedImageName, actualFirstImage.imageName)
        assertEquals(expectedImageName, actualRemediation.currentImage!!.name)
        assertEquals(8, actualFirstImage.workloadImages.first().lineNumber)
    }

    @Test
    fun `take image from KubernetesImageCache and scan it using the CLI, no remediation`() {
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

    @Test
    fun `take all images from KubernetesImageCache and scan them using the CLI`() {
        // create KubernetesImageCache mock
        val cache = spyk(KubernetesImageCache(project))
        val fakePsiFile = createFile("fake.file", "")
        every { cache.getKubernetesWorkloadImages() } returns
            setOf(
                KubernetesWorkloadImage("debian", fakePsiFile),
                KubernetesWorkloadImage("fake-image-name", fakePsiFile),
                KubernetesWorkloadImage("nginx", fakePsiFile)
            )
        cut.setKubernetesImageCache(cache)
        // create CLI mock
        val mockkRunner = mockk<ConsoleCommandRunner>()
        every { mockkRunner.execute(any(), any(), any(), project) } returns containerResultForFewImagesJson
        cut.setConsoleCommandRunner(mockkRunner)

        val containerResult = cut.scan()

        verify { cache.getKubernetesWorkloadImages() }
        verify {
            cut.execute(
                listOf("container", "test", "debian", "fake-image-name", "nginx")
            )
        }
        assertTrue("Container scan should succeed", containerResult.isSuccessful())
        val allCliIssues = containerResult.allCliIssues
        assertTrue("Images with issues should be found", allCliIssues != null)
        allCliIssues!!
        assertTrue(
            "2 images (debian and nginx) with issues should be found",
            allCliIssues.size == 2 &&
                allCliIssues.any { it.imageName == "debian" } &&
                allCliIssues.any { it.imageName == "nginx" }
        )
    }
}
