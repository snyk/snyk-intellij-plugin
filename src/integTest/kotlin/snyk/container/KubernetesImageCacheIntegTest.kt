package snyk.container

import com.intellij.openapi.components.service
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightPlatform4TestCase
import io.snyk.plugin.getKubernetesImageCache
import org.junit.Test
import snyk.container.TestYamls.cronJobYaml
import snyk.container.TestYamls.daemonSetYaml
import snyk.container.TestYamls.deploymentYaml
import snyk.container.TestYamls.fallbackTest
import snyk.container.TestYamls.jobYaml
import snyk.container.TestYamls.multiContainerPodYaml
import snyk.container.TestYamls.podYaml
import snyk.container.TestYamls.replicaSetWithoutApiVersionYaml
import snyk.container.TestYamls.replicaSetYaml
import snyk.container.TestYamls.replicationControllerYaml
import snyk.container.TestYamls.statefulSetYaml

private const val podYamlLineNumber = 8

@Suppress("FunctionName")
class KubernetesImageCacheIntegTest : LightPlatform4TestCase() {
    private val fileName = "KubernetesImageExtractorIntegTest.yaml"

    private lateinit var cut: KubernetesImageCache
    override fun setUp() {
        super.setUp()
        cut = project.service()
        getKubernetesImageCache(project)?.clear()
    }

    @Test
    fun `extractFromFile should find yaml files and extract images`() {
        val file = createFile(fileName, podYaml())
        cut.extractFromFile(file.virtualFile)
        val images = cut.getKubernetesWorkloadImages()

        assertEquals(1, images.size)
        assertEquals(KubernetesWorkloadImage("nginx:1.16.0", file, 8), images.first())
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

        cut.extractFromFile(file)
        val files: Set<VirtualFile> = cut.getKubernetesWorkloadFilesFromCache()

        assertEquals(1, files.size)
        assertTrue(files.contains(file))
    }

    @Test
    fun `should not parse non yaml files`() {
        val file = createFile("not-a-yaml-file.zaml", podYaml()).virtualFile

        cut.extractFromFile(file)
        val files: Set<VirtualFile> = cut.getKubernetesWorkloadFilesFromCache()

        assertEmpty(files)
    }

    @Test
    fun `should return Kubernetes Workload Image Names`() {
        val file = createFile(fileName, podYaml()).virtualFile

        cut.extractFromFile(file)
        val images: Set<String> = cut.getKubernetesWorkloadImageNamesFromCache()

        assertEquals(1, images.size)
        assertTrue(images.contains("nginx:1.16.0"))
    }

    @Test
    fun `should return Kubernetes Workload Images with correct line number`() {
        val file = createFile(fileName, podYaml()).virtualFile

        cut.extractFromFile(file)
        val images: Set<KubernetesWorkloadImage> = cut.getKubernetesWorkloadImages()

        assertEquals(1, images.size)
        assertEquals(podYamlLineNumber, images.first().lineNumber)
    }

    private fun executeExtract(yaml: String): Set<String> {
        val file = createFile(fileName, yaml).virtualFile

        cut.extractFromFile(file)
        return cut.getKubernetesWorkloadImageNamesFromCache()
    }
}
