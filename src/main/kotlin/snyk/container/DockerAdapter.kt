package snyk.container

import org.testcontainers.DockerClientFactory

class DockerAdapter {
    fun isDockerRunning(): Boolean = DockerClientFactory.instance().isDockerAvailable
}
