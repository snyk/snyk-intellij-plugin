package io.snyk.plugin

import io.snyk.plugin.client.{SnykCLIClient, SnykConfig}
import io.snyk.plugin.datamodel.{SecurityVuln, SnykMavenArtifact}
import io.snyk.plugin.ui.state.SnykPluginState
import org.junit.Assert.{assertEquals, assertNotNull, assertTrue}
import org.junit.Test

import scala.io.{Codec, Source}

class SnykCLIClientTest extends AbstractMavenTestCase() {

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

    val vulnerabilities = triedSnykVulnResponse.get.vulnerabilities

    assertEquals("One vulnerability expected", 1, vulnerabilities.size)
    assertEquals("org.codehaus.jackson:jackson-mapper-asl",
      vulnerabilities.head.asInstanceOf[SecurityVuln].moduleName)
  }

  @Test
  def testUserInfoEndpoint(): Unit = {
    val config = SnykConfig.default
    val apiClient = SnykCLIClient.standard(config)

    assertNotNull(apiClient)
    assertTrue(apiClient.userInfo().isSuccess)
  }
}
