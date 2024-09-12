package snyk.container

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.io.createDirectories
import com.intellij.util.io.delete
import io.mockk.mockk
import io.mockk.unmockkAll
import io.snyk.plugin.getKubernetesImageCache
import io.snyk.plugin.getSnykCachedResults
import io.snyk.plugin.resetSettings
import io.snyk.plugin.ui.toolwindow.SnykToolWindowPanel
import org.awaitility.Awaitility.await
import org.eclipse.lsp4j.services.LanguageServer
import snyk.common.lsp.LanguageServerWrapper
import snyk.container.ui.ContainerImageTreeNode
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Paths
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlin.io.path.notExists

class ContainerBulkFileListenerTest : BasePlatformTestCase() {
    private val lsMock = mockk<LanguageServer>(relaxed = true)

    override fun setUp() {
        super.setUp()
        unmockkAll()
        resetSettings(project)
        LanguageServerWrapper.getInstance().languageServer = lsMock
        LanguageServerWrapper.getInstance().isInitialized = true
    }

    override fun tearDown() {
        unmockkAll()
        resetSettings(project)
        try {
            super.tearDown()
        } catch (ignore: Exception) {
            // nothing to do, as we are in test shutdown
        }
    }

    private val imageCache get() = project.service<KubernetesImageCache>()

    private fun setUpContainerTest() {
        imageCache.clear()
    }

    private fun createNewFileInProjectRoot(): File {
        val projectPath = Paths.get(project.basePath!!)
        if (projectPath.notExists(LinkOption.NOFOLLOW_LINKS)) {
            projectPath.createDirectories()
        }
        return File(project.basePath + File.separator + "kubernetes-test.yaml").apply { createNewFile() }
    }

    fun `test Container should update image cache when yaml file is changed`() {
        setUpContainerTest()
        val path = createNewFileInProjectRoot().toPath()
        Files.write(path, "\n".toByteArray(Charsets.UTF_8))
        var virtualFile: VirtualFile? = null
        invokeLater {
            VirtualFileManager.getInstance().syncRefresh()
            virtualFile = VirtualFileManager.getInstance().findFileByNioPath(path)
        }
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        await().timeout(5, TimeUnit.SECONDS).until { virtualFile?.isValid ?: false }


        ApplicationManager.getApplication().runWriteAction {
            val file = PsiManager.getInstance(project).findFile(virtualFile!!)
            require(file != null)
            PsiDocumentManager.getInstance(project).getDocument(file)
                ?.setText(TestYamls.podYaml())
        }
        FileDocumentManager.getInstance().saveAllDocuments()
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

        var kubernetesWorkloadImages = imageCache.getKubernetesWorkloadImages()
        await().atMost(5, TimeUnit.SECONDS).until {
            kubernetesWorkloadImages = imageCache.getKubernetesWorkloadImages()
            kubernetesWorkloadImages.isNotEmpty()
        }
        assertNotEmpty(kubernetesWorkloadImages)
        assertEquals(1, kubernetesWorkloadImages.size)
        assertEquals(path, kubernetesWorkloadImages.first().virtualFile.toNioPath())
        assertEquals("nginx:1.16.0", kubernetesWorkloadImages.first().image)
        virtualFile!!.toNioPath().delete(true)
    }

    fun `test Container should delete images from cache when yaml file is deleted`() {
        setUpContainerTest()
        val file = myFixture.addFileToProject("kubernetes-test.yaml", "")

        ApplicationManager.getApplication().runWriteAction {
            PsiDocumentManager.getInstance(project).getDocument(file)
                ?.setText(TestYamls.podYaml())
        }
        FileDocumentManager.getInstance().saveAllDocuments()
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

        var kubernetesWorkloadImages: Set<KubernetesWorkloadImage>
        await().atMost(5, TimeUnit.SECONDS).until {
            kubernetesWorkloadImages = imageCache.getKubernetesWorkloadImages()
            kubernetesWorkloadImages.isNotEmpty()
        }

        ApplicationManager.getApplication().runWriteAction {
            file.virtualFile.delete(null)
        }
        FileDocumentManager.getInstance().saveAllDocuments()
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

        await().atMost(5, TimeUnit.SECONDS).until {
            imageCache.getKubernetesWorkloadImages().isEmpty()
        }
    }

    /**
     * `psiFile == null` is the case when we want to check if _any_ Container file with issues been marked as obsolete
     */
    private fun containerCacheInvalidatedForFile(psiFile: PsiFile?): Boolean {
        val containerCachedIssues = getSnykCachedResults(project)?.currentContainerResult!!.allCliIssues!!
        return containerCachedIssues.any { issuesForImage ->
            (psiFile == null || issuesForImage.workloadImages.any { it.virtualFile == psiFile.virtualFile }) &&
                issuesForImage.obsolete
        }
    }

    private fun isContainerUpdateNeeded(): Boolean =
        getSnykCachedResults(project)?.currentContainerResult?.rescanNeeded ?: true

    private fun createFakeContainerResultInCache(psiFile: PsiFile? = null) {
        val containerIssue = ContainerIssue(
            id = "fake id",
            title = "fake title",
            description = "fake description",
            severity = "",
            from = emptyList(),
            packageManager = "npm"
        )
        val addedPsiFile = psiFile ?: myFixture.configureByText("fake.yaml", TestYamls.podYaml())
        val issuesForImage = ContainerIssuesForImage(
            vulnerabilities = listOf(containerIssue),
            projectName = "fake project name",
            docker = Docker(),
            uniqueCount = 1,
            error = null,
            imageName = "nginx",
            workloadImages = listOf(
                KubernetesWorkloadImage(
                    image = "nginx",
                    virtualFile = addedPsiFile.virtualFile
                )
            )
        )
        val fakeContainerResult = ContainerResult(listOf(issuesForImage))
        val toolWindowPanel = project.service<SnykToolWindowPanel>()
        getSnykCachedResults(project)?.currentContainerResult = fakeContainerResult
        toolWindowPanel.getRootContainerIssuesTreeNode().add(ContainerImageTreeNode(issuesForImage, project) {})

        getKubernetesImageCache(project)?.extractFromFileAndAddToCache(addedPsiFile.virtualFile)
    }

    fun `test ContainerResults should drop cache and mark rescanNeeded when Container supported file CHANGED`() {
        setUpContainerTest()
        val psiFile = myFixture.configureByText("existing.yaml", TestYamls.podYaml())
        createFakeContainerResultInCache(psiFile)

        assertFalse(isContainerUpdateNeeded())

        ApplicationManager.getApplication().runWriteAction {
            PsiDocumentManager.getInstance(project).getDocument(psiFile)?.setText(TestYamls.podYaml() + "bla bla")
        }
        FileDocumentManager.getInstance().saveAllDocuments()

        assertTrue(isContainerUpdateNeeded())
        await().atMost(2, TimeUnit.SECONDS).until { containerCacheInvalidatedForFile(psiFile) }
    }

    fun `test ContainerResults should mark rescanNeeded when Container supported file CREATED`() {
        setUpContainerTest()
        createFakeContainerResultInCache()

        assertFalse(isContainerUpdateNeeded())

        myFixture.configureByText("new.yaml", TestYamls.podYaml())

        assertTrue(isContainerUpdateNeeded())
        assertFalse(
            "None of Container file with issues should been marked as obsolete here",
            containerCacheInvalidatedForFile(null)
        )
    }

    fun `test ContainerResults should mark rescanNeeded when Container supported file COPIED`() {
        setUpContainerTest()
        val originalFile = myFixture.addFileToProject("existing.yaml", TestYamls.podYaml())
        createFakeContainerResultInCache(originalFile)

        assertFalse(isContainerUpdateNeeded())

        lateinit var newFile: VirtualFile
        ApplicationManager.getApplication().runWriteAction {
            newFile = originalFile.virtualFile.copy(null, originalFile.virtualFile.parent, "copied.yaml")
        }
        myFixture.configureFromExistingVirtualFile(newFile)

        assertTrue(isContainerUpdateNeeded())
        assertFalse(
            "None of Container file with issues should been marked as obsolete here",
            containerCacheInvalidatedForFile(null)
        )
    }

    fun `test ContainerResults should drop cache and mark rescanNeeded when Container supported file MOVED`() {
        setUpContainerTest()
        val originalFile = myFixture.addFileToProject("existing.yaml", TestYamls.podYaml())
        createFakeContainerResultInCache(originalFile)

        assertFalse(isContainerUpdateNeeded())

        ApplicationManager.getApplication().runWriteAction {
            val moveToDirVirtualFile = originalFile.virtualFile.parent.createChildDirectory(null, "subdir")
            originalFile.virtualFile.move(null, moveToDirVirtualFile)
        }

        assertTrue(isContainerUpdateNeeded())
        assertTrue(
            "Moved Container file with issues should been marked as obsolete here",
            containerCacheInvalidatedForFile(originalFile)
        )
    }

    fun `test ContainerResults should drop cache and mark rescanNeeded when Container supported file RENAMED`() {
        setUpContainerTest()
        val originalFile = myFixture.addFileToProject("existing.yaml", TestYamls.podYaml())
        createFakeContainerResultInCache(originalFile)

        assertFalse(isContainerUpdateNeeded())

        ApplicationManager.getApplication().runWriteAction {
            originalFile.virtualFile.rename(null, "renamed_filename.txt")
        }

        assertTrue(isContainerUpdateNeeded())
        assertTrue(
            "Renamed Container file with issues should been marked as obsolete here",
            containerCacheInvalidatedForFile(originalFile)
        )
    }

    fun `test ContainerResults should drop cache and mark rescanNeeded when Container supported file DELETED`() {
        setUpContainerTest()
        val originalFile = myFixture.addFileToProject("existing.yaml", TestYamls.podYaml())
        createFakeContainerResultInCache(originalFile)

        assertFalse(isContainerUpdateNeeded())

        ApplicationManager.getApplication().runWriteAction {
            originalFile.virtualFile.delete(null)
        }

        assertTrue(isContainerUpdateNeeded())
        assertTrue(
            "Deleted Container file with issues should been marked as obsolete here",
            containerCacheInvalidatedForFile(originalFile)
        )
    }

    fun `test ContainerResults should drop cache and mark rescanNeeded when cached file wiped it content`() {
        setUpContainerTest()
        val psiFile = myFixture.addFileToProject("existing.yaml", TestYamls.podYaml())
        createFakeContainerResultInCache(psiFile)
        assertFalse(isContainerUpdateNeeded())

        ApplicationManager.getApplication().runWriteAction {
            PsiDocumentManager.getInstance(project).getDocument(psiFile)?.setText("")
        }
        FileDocumentManager.getInstance().saveAllDocuments()

        assertTrue(isContainerUpdateNeeded())
        assertTrue(
            "Cached Container file with removed content should been marked as obsolete here",
            containerCacheInvalidatedForFile(psiFile)
        )
    }

    fun `test Container should update cache even if any other cache update fail with Exception`() {
        setUpContainerTest()
        val psiFile = myFixture.configureByText("existing.yaml", TestYamls.podYaml())
        createFakeContainerResultInCache(psiFile)
        assertFalse(isContainerUpdateNeeded())

        val exceptionThrown: Boolean
        val messageBusConnection = ApplicationManager.getApplication().messageBus.connect()
        try {
            messageBusConnection.subscribe(VirtualFileManager.VFS_CHANGES, ExceptionProducerFileListener())

            ApplicationManager.getApplication().runWriteAction {
                PsiDocumentManager.getInstance(project).getDocument(psiFile)?.setText(TestYamls.podYaml() + "bla bla")
            }
            exceptionThrown = try {
                FileDocumentManager.getInstance().saveAllDocuments()
                false
            } catch (ignored: RuntimeException) {
                true
            }
        } finally {
            messageBusConnection.dispose()
        }

        assertTrue("control Exception should thrown during that test", exceptionThrown)
        assertTrue(isContainerUpdateNeeded())
        await().atMost(2, TimeUnit.SECONDS).until { containerCacheInvalidatedForFile(psiFile) }
    }

    private class ControlException : RuntimeException()

    inner class ExceptionProducerFileListener : BulkFileListener {
        override fun after(events: MutableList<out VFileEvent>) {
            throw ControlException()
        }
    }
}
