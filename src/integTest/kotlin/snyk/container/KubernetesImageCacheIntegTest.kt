package snyk.container

import com.intellij.openapi.components.service
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightPlatform4TestCase
import io.snyk.plugin.getKubernetesImageCache
import org.junit.Test

class KubernetesImageCacheIntegTest : LightPlatform4TestCase() {
    private val fileName = "KubernetesImageExtractorIntegTest.yaml"

    private lateinit var cut: KubernetesImageCache
    override fun setUp() {
        super.setUp()
        cut = project.service()
        getKubernetesImageCache(project).clear()
    }

    @Test
    fun `extractFromFile should find yaml files and extract images`() {
        val file = createFile(fileName, podYaml())
        cut.extractFromFile(file.virtualFile)
        val images = cut.getKubernetesWorkloadImages()

        assertEquals(1, images.size)
        assertTrue(images.contains(KubernetesWorkloadImage("nginx:1.14.2", file)))
    }

    @Test
    fun `should extract images from multi-container pod yaml`() {
        val images = executeExtract(multiContainerPodYaml())

        assertTrue(images.contains("nginx:1.14.2"))
        assertTrue(images.contains("busybox"))
        assertEquals(2, images.size)
    }

    @Test
    fun `should extract images from jobs`() {
        val images = executeExtract(jobYaml())

        assertEquals(1, images.size)
        assertTrue(images.contains("perl"))
    }

    private fun jobYaml(): String =
        """
        apiVersion: batch/v1
        kind: Job
        metadata:
          name: pi
        spec:
          template:
            spec:
              containers:
              - name: pi
                image: perl
                command: ["perl",  "-Mbignum=bpi", "-wle", "print bpi(2000)"]
              restartPolicy: Never
          backoffLimit: 4
        """.trimIndent()

    @Test
    fun `should extract images from cronjobs`() {
        val images = executeExtract(cronJobYaml())

        assertEquals(1, images.size)
        assertTrue(images.contains("busybox"))
    }

    private fun cronJobYaml(): String =
        """
        apiVersion: batch/v1
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

    @Test
    fun `should extract via fallback`() {
        val images = executeExtract(fallbackTest())

        assertEquals(1, images.size)
        assertTrue(images.contains("busybox"))
    }

    private fun fallbackTest(): String =
        """
        apiVersion: fallbacktest/v1
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

    @Test
    fun `should extract from stateful set`() {
        val images = executeExtract(statefulSetYaml())

        assertEquals(1, images.size)
        assertTrue(images.contains("k8s.gcr.io/nginx-slim:0.8"))
    }

    private fun statefulSetYaml(): String =
        """
        apiVersion: apps/v1
        kind: StatefulSet
        metadata:
          name: web
        spec:
          selector:
            matchLabels:
              app: nginx # has to match .spec.template.metadata.labels
          serviceName: "nginx"
          replicas: 3 # by default is 1
          template:
            metadata:
              labels:
                app: nginx # has to match .spec.selector.matchLabels
            spec:
              terminationGracePeriodSeconds: 10
              containers:
              - name: nginx
                image: k8s.gcr.io/nginx-slim:0.8
                ports:
                - containerPort: 80
                  name: web
                volumeMounts:
                - name: www
                  mountPath: /usr/share/nginx/html
          volumeClaimTemplates:
          - metadata:
              name: www
            spec:
              accessModes: [ "ReadWriteOnce" ]
              storageClassName: "my-storage-class"
              resources:
                requests:
                  storage: 1Gi
        """.trimIndent()

    @Test
    fun `should extract from daemon set`() {
        val images = executeExtract(daemonSetYaml())

        assertEquals(1, images.size)
        assertTrue(images.contains("quay.io/fluentd_elasticsearch/fluentd:v2.5.2"))
    }

    private fun daemonSetYaml(): String =
        """
        apiVersion: apps/v1
        kind: DaemonSet
        metadata:
          name: fluentd-elasticsearch
          namespace: kube-system
          labels:
            k8s-app: fluentd-logging
        spec:
          selector:
            matchLabels:
              name: fluentd-elasticsearch
          template:
            metadata:
              labels:
                name: fluentd-elasticsearch
            spec:
              tolerations:
              # this toleration is to have the daemonset runnable on master nodes
              # remove it if your masters can't run pods
              - key: node-role.kubernetes.io/master
                operator: Exists
                effect: NoSchedule
              containers:
              - name: fluentd-elasticsearch
                image: quay.io/fluentd_elasticsearch/fluentd:v2.5.2
                resources:
                  limits:
                    memory: 200Mi
                  requests:
                    cpu: 100m
                    memory: 200Mi
                volumeMounts:
                - name: varlog
                  mountPath: /var/log
                - name: varlibdockercontainers
                  mountPath: /var/lib/docker/containers
                  readOnly: true
              terminationGracePeriodSeconds: 30
              volumes:
              - name: varlog
                hostPath:
                  path: /var/log
              - name: varlibdockercontainers
                hostPath:
                  path: /var/lib/docker/containers
              """.trimIndent()

    private fun multiContainerPodYaml(): String =
        """
        apiVersion: v1
        kind: Pod
        metadata:
          name: nginx
        spec:
          containers:
          - name: nginx
            image: nginx:1.14.2
          - name: busybox
            image: busybox
            ports:
            - containerPort: 80
        """.trimIndent()

    @Test
    fun `should extract from deployment`() {
        val images = executeExtract(deploymentYaml())

        assertEquals(1, images.size)
        assertTrue(images.contains("nginx:1.14.2"))
    }

    private fun deploymentYaml(): String =
        """
        apiVersion: apps/v1
        kind: Deployment
        metadata:
          name: nginx-deployment
        spec:
          selector:
            matchLabels:
              app: nginx
          replicas: 2 # tells deployment to run 2 pods matching the template
          template:
            metadata:
              labels:
                app: nginx
            spec:
              containers:
              - name: nginx
                image: nginx:1.14.2
                ports:
                - containerPort: 80
        """.trimIndent()

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

    private fun replicaSetYaml(): String =
        """
        apiVersion: apps/v1
        kind: ReplicaSet
        metadata:
          name: frontend
          labels:
            app: guestbook
            tier: frontend
        spec:
          # modify replicas according to your case
          replicas: 3
          selector:
            matchLabels:
              tier: frontend
          template:
            metadata:
              labels:
                tier: frontend
            spec:
              containers:
              - name: php-redis
                image: gcr.io/google_samples/gb-frontend:v3
        """.trimIndent()

    private fun replicaSetWithoutApiVersionYaml(): String =
        """
        kind: ReplicaSet
        metadata:
          name: frontend
          labels:
            app: guestbook
            tier: frontend
        spec:
          # modify replicas according to your case
          replicas: 3
          selector:
            matchLabels:
              tier: frontend
          template:
            metadata:
              labels:
                tier: frontend
            spec:
              containers:
              - name: php-redis
                image: gcr.io/google_samples/gb-frontend:v3
        """.trimIndent()

    @Test
    fun `should extract from replication controller`() {
        val images = executeExtract(replicationControllerYaml())

        assertEquals(1, images.size)
        assertTrue(images.contains("nginx"))
    }

    private fun replicationControllerYaml(): String =
        """
        apiVersion: v1
        kind: ReplicationController
        metadata:
          name: nginx
        spec:
          replicas: 3
          selector:
            app: nginx
          template:
            metadata:
              name: nginx
              labels:
                app: nginx
            spec:
              containers:
              - name: nginx
                image: nginx
                ports:
                - containerPort: 80
        """.trimIndent()

    private fun executeExtract(yaml: String): Set<String> {
        val file = createFile(fileName, yaml).virtualFile

        cut.extractFromFile(file)
        return cut.getKubernetesWorkloadImageNamesFromCache()
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
        assertTrue(images.contains("nginx:1.14.2"))
    }

    fun podYaml(): String =
        """
        apiVersion: v1
        kind: Pod
        metadata:
          name: nginx
        spec:
          containers:
          - name: nginx
            image: nginx:1.14.2
            ports:
            - containerPort: 80
        """.trimIndent()
}
