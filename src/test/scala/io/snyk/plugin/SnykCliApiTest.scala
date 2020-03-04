package io.snyk.plugin

import io.snyk.plugin.datamodel.{SecurityVuln, SnykMavenArtifact}
import io.snyk.plugin.depsource.ProjectType
import io.snyk.plugin.ui.state.SnykPluginState
import org.junit.Assert.{assertEquals, assertTrue}
import org.junit.Test

import scala.io.{Codec, Source}

class SnykCliApiTest extends AbstractMavenTestCase() {

  override protected def setUp(): Unit = {
    super.setUp()

    val projectXmlStr = Source.fromResource("sample-pom.xml", getClass.getClassLoader)(Codec.UTF8).mkString

    importProject(projectXmlStr)
  }

  @Test
  def testRunScan(): Unit = {
    val snykPluginState = SnykPluginState.forIntelliJ(currentProject)

    val mavenArtifact = SnykMavenArtifact(
      "<none>",
      "<none>",
      "<none>",
      "<none>",
      None,
      None,
      Nil,
      ProjectType.MAVEN
    )

    val snykVulnResponse = snykPluginState.apiClient.runScan(currentProject, mavenArtifact)

    assertTrue(snykVulnResponse.isSuccess)

    val vulnerabilities = snykVulnResponse.get.head.vulnerabilities

    assertEquals("One vulnerability expected", 1, vulnerabilities.size)
    assertEquals("org.codehaus.jackson:jackson-mapper-asl",
      vulnerabilities.head.asInstanceOf[SecurityVuln].moduleName)
  }
}
