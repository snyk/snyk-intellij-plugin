package io.snyk.plugin

import io.snyk.plugin.datamodel.{SecurityVuln, SnykMavenArtifact}
import io.snyk.plugin.ui.state.SnykPluginState
import org.junit.Assert.{assertEquals, assertTrue}
import org.junit.Test

import scala.io.{Codec, Source}

class SnykCLIApiClientTest extends AbstractMavenTestCase() {

  override protected def setUp(): Unit = {
    super.setUp()

    val projectXmlStr = Source.fromResource("sample-pom.xml", getClass.getClassLoader)(Codec.UTF8).mkString

    importProject(projectXmlStr)
  }

  @Test
  def testRunScan(): Unit = {
    val snykPluginState = SnykPluginState.forIntelliJ(currentProject)

    val triedSnykVulnResponse = snykPluginState.apiClient.runScan(currentProject, SnykMavenArtifact.empty)

    assertTrue(triedSnykVulnResponse.isSuccess)

    val vulnerabilities = triedSnykVulnResponse.get.head.vulnerabilities

    assertEquals("One vulnerability expected", 1, vulnerabilities.size)
    assertEquals("org.codehaus.jackson:jackson-mapper-asl",
      vulnerabilities.head.asInstanceOf[SecurityVuln].moduleName)
  }
}
