package io.snyk.plugin

import java.util

import io.snyk.plugin.client.{CliClient, ConsoleCommandRunner, SnykConfig}
import io.snyk.plugin.datamodel.{SecurityVuln, ProjectDependency}
import io.snyk.plugin.depsource.ProjectType
import io.snyk.plugin.ui.state.SnykPluginState
import org.junit.Assert.{assertEquals, assertFalse, assertNotNull, assertTrue}
import org.junit.Test

import scala.io.{Codec, Source}

class SnykCliClientTest extends AbstractMavenTestCase() {

  override protected def setUp(): Unit = {
    super.setUp()

    val projectXmlStr = Source.fromResource("sample-pom.xml", getClass.getClassLoader)(Codec.UTF8).mkString

    importProject(projectXmlStr)
  }

  @Test
  def testUserInfoEndpoint(): Unit = {
    val config = SnykConfig.default
    val apiClient = CliClient.standard(config)

    assertNotNull(apiClient)
    assertTrue(apiClient.userInfo().isSuccess)
  }

  @Test
  def testRunScan(): Unit = {
    val snykPluginState = SnykPluginState.forIntelliJ(currentProject)

    val mavenArtifact = ProjectDependency(
      "<none>",
      "<none>",
      "<none>",
      "<none>",
      None,
      None,
      Nil,
      ProjectType.MAVEN,
      false
    )

    val snykVulnResponse = snykPluginState.apiClient.runScan(currentProject, mavenArtifact)

    assertTrue(snykVulnResponse.isSuccess)

    val vulnerabilities = snykVulnResponse.get.head.vulnerabilities

    assertEquals("One vulnerability expected", 1, vulnerabilities.size)
    assertEquals("org.codehaus.jackson:jackson-mapper-asl",
      vulnerabilities.head.head.asInstanceOf[SecurityVuln].moduleName)
  }

  @Test
  def testIsCliInstalledFailed(): Unit = {
    val snykPluginState = SnykPluginState.forIntelliJ(currentProject)

    val isCliInstalled = snykPluginState.apiClient.isCliInstalled(new ConsoleCommandRunner() {
      override def execute(commands: util.ArrayList[String], workDirectory: String): String = {
        "command not found"
      }
    })

    assertFalse(isCliInstalled)
  }

  @Test
  def testIsCliInstalledSuccess(): Unit = {
    val snykPluginState = SnykPluginState.forIntelliJ(currentProject)

    val isCliInstalled = snykPluginState.apiClient.isCliInstalled(new ConsoleCommandRunner() {
      override def execute(commands: util.ArrayList[String], workDirectory: String): String = {
        "1.290.2"
      }
    })

    assertTrue(isCliInstalled)
  }
}
