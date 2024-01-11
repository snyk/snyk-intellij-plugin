package snyk.container

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.LightPlatform4TestCase
import io.mockk.unmockkAll
import io.snyk.plugin.getKubernetesImageCache
import org.junit.Test
import snyk.container.TestYamls.cronJobErroneousYaml
import snyk.container.TestYamls.cronJobYaml
import snyk.container.TestYamls.daemonSetYaml
import snyk.container.TestYamls.deploymentYaml
import snyk.container.TestYamls.duplicatedImageNameYaml
import snyk.container.TestYamls.fallbackTest
import snyk.container.TestYamls.helmYaml
import snyk.container.TestYamls.imagePathCommentedYaml
import snyk.container.TestYamls.imagePathFollowedByCommentYaml
import snyk.container.TestYamls.imagePathWithDigestYaml
import snyk.container.TestYamls.imagePathWithPortAndTagYaml
import snyk.container.TestYamls.jobYaml
import snyk.container.TestYamls.multiContainerPodYaml
import snyk.container.TestYamls.podYaml
import snyk.container.TestYamls.replicaSetWithoutApiVersionYaml
import snyk.container.TestYamls.replicaSetYaml
import snyk.container.TestYamls.replicationControllerYaml
import snyk.container.TestYamls.singleQuoteImageNameBrokenYaml
import snyk.container.TestYamls.statefulSetYaml

private const val podYamlLineNumber = 8

@Suppress("FunctionName")
class KubernetesImageCacheIntegTest : LightPlatform4TestCase() {
    private val fileName = "KubernetesImageExtractorIntegTest.yaml"

    private lateinit var cut: KubernetesImageCache
    override fun setUp() {
        super.setUp()
        unmockkAll()
        cut = project.service()
        getKubernetesImageCache(project)?.clear()
    }

    override fun tearDown() {
        unmockkAll()
        super.tearDown()
    }

    @Test
    fun `extractFromFile should find yaml files and extract images`() {
        val file = createFile(fileName, podYaml()).virtualFile
        cut.extractFromFileAndAddToCache(file)
        val images = cut.getKubernetesWorkloadImages()

        assertEquals(1, images.size)
        assertEquals(KubernetesWorkloadImage("nginx:1.16.0", file, 8, 85), images.first())
    }

    @Test
    fun `should extract images from multi-container pod yaml`() {
        val images = executeExtract(multiContainerPodYaml())

        assertTrue(images.contains("nginx:1.16.0"))
        assertTrue(images.contains("busybox"))
        assertEquals(2, images.size)
    }

    @Test
    fun `should extract images from jobs`() {
        val images = executeExtract(jobYaml())

        assertEquals(1, images.size)
        assertTrue(images.contains("perl"))
    }

    @Test
    fun `should extract images from cronjobs`() {
        val images = executeExtract(cronJobYaml())

        assertEquals(1, images.size)
        assertTrue(images.contains("busybox"))
    }

    @Test
    fun `should extract no images from erroneous cronjobs`() {
        val images = executeExtract(cronJobErroneousYaml())

        assertEquals(0, images.size)
    }

    @Test
    fun `should extract via fallback`() {
        val images = executeExtract(fallbackTest())

        assertEquals(1, images.size)
        assertTrue(images.contains("busybox"))
    }

    @Test
    fun `should extract from stateful set`() {
        val images = executeExtract(statefulSetYaml())

        assertEquals(1, images.size)
        assertTrue(images.contains("k8s.gcr.io/nginx-slim:0.8"))
    }

    @Test
    fun `should extract from daemon set`() {
        val images = executeExtract(daemonSetYaml())

        assertEquals(1, images.size)
        assertTrue(images.contains("quay.io/fluentd_elasticsearch/fluentd:v2.5.2"))
    }

    @Test
    fun `should extract from deployment`() {
        val images = executeExtract(deploymentYaml())

        assertEquals(1, images.size)
        assertTrue(images.contains("nginx:1.16.0"))
    }

    @Test
    fun `should extract from replicaset`() {
        val images = executeExtract(replicaSetYaml())

        assertEquals(1, images.size)
        assertTrue(images.contains("gcr.io/google_samples/gb-frontend:v3"))
    }

    @Test
    fun `should not extract from replicaset without apiVersion`() {
        val images = executeExtract(replicaSetWithoutApiVersionYaml())

        assertEquals(0, images.size)
    }

    @Test
    fun `should extract from replication controller`() {
        val images = executeExtract(replicationControllerYaml())

        assertEquals(1, images.size)
        assertTrue(images.contains("nginx"))
    }

    @Test
    fun `should return Kubernetes Workload Files`() {
        val file = createFile(fileName, podYaml()).virtualFile

        cut.extractFromFileAndAddToCache(file)
        val files: Set<VirtualFile> = cut.getKubernetesWorkloadFilesFromCache()

        assertEquals(1, files.size)
        assertTrue(files.contains(file))
    }

    @Test
    fun `should not parse non yaml files`() {
        val file = createFile("not-a-yaml-file.zaml", podYaml()).virtualFile

        cut.extractFromFileAndAddToCache(file)
        val files: Set<VirtualFile> = cut.getKubernetesWorkloadFilesFromCache()

        assertEmpty(files)
    }

    @Test
    fun `should return Kubernetes Workload Image Names`() {
        val file = createFile(fileName, podYaml()).virtualFile

        cut.extractFromFileAndAddToCache(file)
        val images: Set<String> = cut.getKubernetesWorkloadImageNamesFromCache()

        assertEquals(1, images.size)
        assertTrue(images.contains("nginx:1.16.0"))
    }

    @Test
    fun `should return Kubernetes Workload Images with correct line number`() {
        val file = createFile(fileName, podYaml()).virtualFile

        cut.extractFromFileAndAddToCache(file)
        val images: Set<KubernetesWorkloadImage> = cut.getKubernetesWorkloadImages()

        assertEquals(1, images.size)
        assertEquals(podYamlLineNumber, images.first().lineNumber)
    }

    @Test
    fun `should remove file from cache when all images from file (cached before) been removed`() {
        val psiFile = createFile(fileName, podYaml())
        val virtualFile = psiFile.virtualFile
        cut.extractFromFileAndAddToCache(virtualFile)
        val controlImages: Set<KubernetesWorkloadImage> = cut.getKubernetesWorkloadImages()
        assertEquals(1, controlImages.size)

        ApplicationManager.getApplication().runWriteAction {
            PsiDocumentManager.getInstance(project).getDocument(psiFile)
                ?.setText("")
        }
        FileDocumentManager.getInstance().saveAllDocuments()
        cut.extractFromFileAndAddToCache(virtualFile)

        val images = cut.getKubernetesWorkloadImages()
        assertEquals(0, images.size)
        val cachedFiles = cut.getKubernetesWorkloadFilesFromCache()
        assertEquals(0, cachedFiles.size)
    }

    private fun executeExtract(yaml: String): Set<String> {
        val file = createFile(fileName, yaml).virtualFile

        cut.extractFromFileAndAddToCache(file)
        return cut.getKubernetesWorkloadImageNamesFromCache()
    }

    @Test
    fun `extract images from Helm generated yaml with image names inside quotes`() {
        val file = createFile(fileName, helmYaml()).virtualFile
        cut.extractFromFileAndAddToCache(file)
        val images = cut.getKubernetesWorkloadImages()

        assertEquals(1, images.size)
        assertEquals(KubernetesWorkloadImage("snyk/code-agent:latest", file, 43, 1113), images.first())
    }

    @Test
    fun `no images extracted from yaml with invalid image names`() {
        val file = createFile(fileName, singleQuoteImageNameBrokenYaml()).virtualFile
        cut.extractFromFileAndAddToCache(file)
        val images = cut.getKubernetesWorkloadImages()

        assertEquals(0, images.size)
    }

    @Test
    fun `distinct Kubernetes Workload Image Names for calling CLI`() {
        val file = createFile(fileName, duplicatedImageNameYaml()).virtualFile

        cut.extractFromFileAndAddToCache(file)
        val images: Set<String> = cut.getKubernetesWorkloadImageNamesFromCache()

        assertEquals(1, images.size)
        assertTrue(images.contains("nginx:1.16.0"))
    }

    @Test
    fun `image Path followed by comment is extracted`() {
        val file = createFile(fileName, imagePathFollowedByCommentYaml()).virtualFile

        cut.extractFromFileAndAddToCache(file)
        val images: Set<String> = cut.getKubernetesWorkloadImageNamesFromCache()

        assertEquals(1, images.size)
        assertTrue(images.contains("imagename"))
    }

    @Test
    fun `image Path commented is NOT extracted`() {
        val file = createFile(fileName, imagePathCommentedYaml()).virtualFile

        cut.extractFromFileAndAddToCache(file)
        val images: Set<String> = cut.getKubernetesWorkloadImageNamesFromCache()

        assertEquals(0, images.size)
    }

    @Test
    fun `image Path with port and tag is extracted`() {
        val file = createFile(fileName, imagePathWithPortAndTagYaml()).virtualFile

        cut.extractFromFileAndAddToCache(file)
        val images: Set<String> = cut.getKubernetesWorkloadImageNamesFromCache()

        assertEquals(1, images.size)
        assertTrue(images.contains("fictional.registry.example:10443/imagename:latest"))
    }

    @Test
    fun `image Path with digest is extracted`() {
        val file = createFile(fileName, imagePathWithDigestYaml()).virtualFile

        cut.extractFromFileAndAddToCache(file)
        val images: Set<String> = cut.getKubernetesWorkloadImageNamesFromCache()

        assertEquals(1, images.size)
        assertTrue(images.contains("fictional.registry.example:10443/imagename@sha256:45b23dee08af5e43a7fea6c4cf9c25ccf269ee113168c19722f87876677c5cb2"))
    }
}
