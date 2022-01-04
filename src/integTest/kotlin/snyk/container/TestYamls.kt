package snyk.container

object TestYamls {
    fun cronJobYaml(): String =
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

    fun jobYaml(): String =
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

    fun fallbackTest(): String =
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

    fun statefulSetYaml(): String =
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

    fun daemonSetYaml(): String =
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

    fun multiContainerPodYaml(): String =
        """
        apiVersion: v1
        kind: Pod
        metadata:
          name: nginx
        spec:
          containers:
          - name: nginx
            image: nginx:1.16.0
          - name: busybox
            image: busybox
            ports:
            - containerPort: 80
        """.trimIndent()

    fun deploymentYaml(): String =
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
                image: nginx:1.16.0
                ports:
                - containerPort: 80
        """.trimIndent()

    fun replicaSetYaml(): String =
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

    fun replicaSetWithoutApiVersionYaml(): String =
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

    fun replicationControllerYaml(): String =
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

    fun podYaml(): String =
        """
        apiVersion: v1
        kind: Pod
        metadata:
          name: nginx
        spec:
          containers:
          - name: nginx
            image: nginx:1.16.0
            ports:
            - containerPort: 80
        """.trimIndent()
}
