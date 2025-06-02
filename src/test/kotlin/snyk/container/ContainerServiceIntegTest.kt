package snyk.container

import com.google.gson.Gson
import com.intellij.openapi.components.service
import com.intellij.testFramework.LightPlatform4TestCase
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.verify
import io.snyk.plugin.getContentRootPaths
import io.snyk.plugin.removeDummyCliFile
import io.snyk.plugin.setupDummyCliFile
import io.snyk.plugin.ui.toolwindow.SnykToolWindowPanel
import org.eclipse.lsp4j.ExecuteCommandParams
import org.eclipse.lsp4j.services.LanguageServer
import org.junit.Test
import snyk.common.lsp.LanguageServerWrapper
import snyk.common.lsp.commands.COMMAND_EXECUTE_CLI
import snyk.container.TestYamls.podYaml
import snyk.trust.WorkspaceTrustSettings
import java.util.concurrent.CompletableFuture
import kotlin.io.path.absolutePathString

class ContainerServiceIntegTest : LightPlatform4TestCase() {
    private lateinit var cut: ContainerService
    private val containerResultWithRemediationJson = javaClass.classLoader
        .getResource(("container-test-results/nginx-with-remediation.json"))!!.readText(Charsets.UTF_8)
    private val containerResultJsonNoRemediation = javaClass.classLoader
        .getResource(("container-test-results/nginx-no-remediation.json"))!!.readText(Charsets.UTF_8)
    private val containerResultForFewImagesJson = javaClass.classLoader
        .getResource(("container-test-results/debian-nginx-fake_critical_only.json"))!!.readText(Charsets.UTF_8)
    private val containerDoubleJenkinsWithPathJson = javaClass.classLoader
        .getResource(("container-test-results/container-double-jenkins-with-path.json"))!!.readText(Charsets.UTF_8)
    private val containerArrayDoubleAuthFailureJson =
        """
            [
              {
                "ok": false,
                "error": "Authentication failed. Please check the API token on https://snyk.io",
                "path": "nginx:1.17.1"
              },
              {
                "ok": false,
                "error": "Authentication failed. Please check the API token on https://snyk.io",
                "path": "nginx:1.22"
              }
            ]
        """.trimIndent()

    val lsMock = mockk<LanguageServer>()

    override fun setUp() {
        super.setUp()
        unmockkAll()
        setupDummyCliFile()
        project.getContentRootPaths().forEach { service<WorkspaceTrustSettings>().addTrustedPath(it.root.absolutePathString())}
        cut = ContainerService(project)
        val languageServerWrapper = LanguageServerWrapper.getInstance(project)
        languageServerWrapper.languageServer = lsMock
        languageServerWrapper.isInitialized = true
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
        assertTrue(actualRemediation.majorUpgrades != null)
        val expectedImageName = expectedResult.allCliIssues!!.first().imageName
        assertEquals(expectedImageName, actualFirstImage.imageName)
        assertEquals(expectedImageName, actualRemediation.currentImage.name)
        assertEquals(8, actualFirstImage.workloadImages.first().lineNumber)
    }

    @Test
    fun `take image from KubernetesImageCache and scan it using the CLI, no remediation`() {
        val (expectedResult, containerResult) = executeScan(containerResultJsonNoRemediation)

        val actualCliIssues = containerResult.allCliIssues!!
        assertTrue(actualCliIssues.isNotEmpty())
        val first = actualCliIssues.first()
        assertFalse("Remediation should be NOT available", first.baseImageRemediationInfo!!.isRemediationAvailable())
        assertEquals(expectedResult.allCliIssues!!.first().imageName, first.imageName)
    }

    private fun executeScan(expectedResult: String): Pair<ContainerResult, ContainerResult> {
        val cache = setupCacheAndFile()

        every { lsMock.workspaceService.executeCommand(any()) } returns CompletableFuture.completedFuture(
            mapOf(
                Pair(
                    "stdOut",
                    expectedResult
                )
            )
        )

        val expectedContainerResult =
            ContainerResult(listOf(Gson().fromJson(expectedResult, ContainerIssuesForImage::class.java)))

        val scanResult = cut.scan()

        verify { cache.getKubernetesWorkloadImages() }
        assertEquals(
            expectedContainerResult.issuesCount,
            scanResult.allCliIssues?.sumOf { it.groupedVulnsById.size }
        )
        return Pair(expectedContainerResult, scanResult)
    }

    private fun setupCacheAndFile(): KubernetesImageCache {
        val fileName = "my-test-pod.yaml"
        val file = createFile(fileName, podYaml())
        val cache = spyk(KubernetesImageCache(project))
        cut.setKubernetesImageCache(cache)
        cache.extractFromFileAndAddToCache(file.virtualFile)
        return cache
    }

    @Test
    fun `take all images from KubernetesImageCache and scan them using the CLI`() {
        // create KubernetesImageCache mock
        val cache = spyk(KubernetesImageCache(project))
        val fakeVirtualFile = createFile("fake.file", "").virtualFile
        every { cache.getKubernetesWorkloadImages() } returns
            setOf(
                KubernetesWorkloadImage("debian", fakeVirtualFile),
                KubernetesWorkloadImage("fake-image-name", fakeVirtualFile),
                KubernetesWorkloadImage("nginx", fakeVirtualFile)
            )
        cut.setKubernetesImageCache(cache)
        every { lsMock.workspaceService.executeCommand(any()) } returns CompletableFuture.completedFuture(
            mapOf(
                Pair(
                    "stdOut",
                    containerResultForFewImagesJson
                )
            )
        )
        val containerResult = cut.scan()

        verify { cache.getKubernetesWorkloadImages() }
        verify {
            val param = ExecuteCommandParams(COMMAND_EXECUTE_CLI, listOf(project.basePath, "container", "test", "debian", "fake-image-name", "nginx", "--json"))
            lsMock.workspaceService.executeCommand(param)
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
        val errors = containerResult.errors
        assertTrue("Image failed to scan should be found", errors.isNotEmpty())
        assertTrue(
            "fake-image-name failed to scan should be found",
            errors.size == 1 && errors.any { it.path == "fake-image-name" }
        )
    }

    /** see [snyk.container.ContainerService.sanitizeImageName] kdoc for bug details */
    @Test
    fun `proceed image with path (registry hostname) correctly bypassing CLI imageName transformation bug`() {
        // create KubernetesImageCache mock
        val cache = spyk(KubernetesImageCache(project))
        val fakeVirtualFile = createFile("fake.file", "").virtualFile
        every { cache.getKubernetesWorkloadImages() } returns
            setOf(
                KubernetesWorkloadImage("jenkins/jenkins", fakeVirtualFile),
                KubernetesWorkloadImage("jenkins/jenkins:lts", fakeVirtualFile)
            )
        cut.setKubernetesImageCache(cache)

        every { lsMock.workspaceService.executeCommand(any()) } returns CompletableFuture.completedFuture(
            mapOf(
                Pair(
                    "stdOut",
                    containerDoubleJenkinsWithPathJson
                )
            )
        )

        val containerResult = cut.scan()

        verify { cache.getKubernetesWorkloadImages() }
        verify {
            val param = ExecuteCommandParams(COMMAND_EXECUTE_CLI, listOf(project.basePath, "container", "test", "jenkins/jenkins", "jenkins/jenkins:lts", "--json"))
            lsMock.workspaceService.executeCommand(param)
        }
        assertTrue("Container scan should succeed", containerResult.isSuccessful())
        val allCliIssues = containerResult.allCliIssues
        assertTrue("Images with issues should be found", allCliIssues != null)
        allCliIssues!!
        assertTrue(
            "2 images correctly named should be found",
            allCliIssues.size == 2 &&
                allCliIssues.any { it.imageName == "jenkins/jenkins" } &&
                allCliIssues.any { it.imageName == "jenkins/jenkins:lts" }
        )
    }

    @Test
    fun `no images to scan case should produce correct ContainerResult`() {
        // create KubernetesImageCache mock
        val cache = spyk(KubernetesImageCache(project))
        every { cache.getKubernetesWorkloadImages() } returns emptySet()
        cut.setKubernetesImageCache(cache)

        val containerResult = cut.scan()

        verify { cache.getKubernetesWorkloadImages() }
        assertFalse("Container scan should NOT succeed", containerResult.isSuccessful())
        val allCliIssues = containerResult.allCliIssues
        assertNull("Images with issues should be NOT found", allCliIssues)
        assertTrue(
            "ContainerResult should hold NO_IMAGES_TO_SCAN_ERROR inside",
            containerResult.getFirstError() == ContainerService.NO_IMAGES_TO_SCAN_ERROR
        )
    }

    @Test
    fun `not Authenticated multi-images scan should produce correct ContainerResult`() {
        // create KubernetesImageCache mock
        val cache = spyk(KubernetesImageCache(project))
        val fakeVirtualFile = createFile("fake.file", "").virtualFile
        every { cache.getKubernetesWorkloadImages() } returns
            setOf(KubernetesWorkloadImage("fake-image", fakeVirtualFile))
        cut.setKubernetesImageCache(cache)
//        // create CLI mock
//        val mockkRunner = mockk<ConsoleCommandRunner>()
//        every { mockkRunner.execute(any(), any(), any(), project) } returns containerArrayDoubleAuthFailureJson
//        cut.setConsoleCommandRunner(mockkRunner)

        every { lsMock.workspaceService.executeCommand(any<ExecuteCommandParams>()) } returns CompletableFuture.completedFuture(
            mapOf(Pair("stdOut", containerArrayDoubleAuthFailureJson))
        )

        val containerResult = cut.scan()

        assertFalse("Container scan should NOT succeed", containerResult.isSuccessful())
        val allCliIssues = containerResult.allCliIssues
        assertNull("Images with issues should be NOT found", allCliIssues)
        assertTrue(
            "ContainerResult should hold CliError with AUTH_FAILED_TEXT inside",
            containerResult.getFirstError()!!.message.startsWith(SnykToolWindowPanel.AUTH_FAILED_TEXT)
        )
    }
}
