package snyk.container

import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import org.junit.Test

class YAMLImageExtractorTest {
    private val yaml =
        """
        --- # comment1
        # comment2
          # comment3

        apiVersion: asdf
        kind: CronJob
        metadata:
          name: hello
        spec:
          schedule: "*/1 * * * *"
          jobTemplate:
            spec:
              template:
                spec:
                  containers:
                  - name: hello
                    image: busybox
                    imagePullPolicy: IfNotPresent
                    command:
                    - /bin/sh
                    - -c
                    - date; echo Hello from the Kubernetes cluster
                  restartPolicy: OnFailure
        """.trimIndent()
    private val yamlList = yaml.split("\n")

    @Test
    fun `isKubernetesFile should return false if apiVersion and kind not set`() {
        var fakeYamlList = yamlList.toMutableList()
        fakeYamlList.removeIf { it.startsWith("apiVersion:") }
        assertFalse(YAMLImageExtractor.isKubernetesFileContent(fakeYamlList))

        fakeYamlList = yamlList.toMutableList()
        fakeYamlList.removeIf { it.startsWith("kind:") }
        assertFalse(YAMLImageExtractor.isKubernetesFileContent(fakeYamlList))

        fakeYamlList.removeIf { it.startsWith("apiVersion:") }
        assertFalse(YAMLImageExtractor.isKubernetesFileContent(fakeYamlList))
    }

    @Test
    fun `isKubernetesFile should return true if apiVersion and kind are set`() {
        assertTrue(YAMLImageExtractor.isKubernetesFileContent(yamlList))
    }
}
