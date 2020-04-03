package io.snyk.plugin

import java.util

import io.snyk.plugin.client.{CliClient, ConsoleCommandRunner, PrepareProjectStatus, SnykConfig}
import io.snyk.plugin.datamodel.{ProjectDependency, SecurityVuln}
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
    val apiClient = CliClient.newInstance(config)

    assertNotNull(apiClient)
    assertTrue(apiClient.userInfo().isSuccess)
  }

  @Test
  def testRunScan(): Unit = {
    val snykPluginState = SnykPluginState.newInstance(currentProject)

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

    val snykVulnResponse = snykPluginState.cliClient.runScan(currentProject, mavenArtifact)

    assertTrue(snykVulnResponse.isSuccess)

    val vulnerabilities = snykVulnResponse.get.head.vulnerabilities

    assertEquals("One vulnerability expected", 1, vulnerabilities.size)
    assertEquals("org.codehaus.jackson:jackson-mapper-asl",
      vulnerabilities.head.head.asInstanceOf[SecurityVuln].moduleName)
  }

  @Test
  def testIsCliInstalledFailed(): Unit = {
    SnykPluginState.removeForProject(currentProject)

    val snykPluginState = SnykPluginState.newInstance(currentProject)

    snykPluginState.cliClient.setConsoleCommandRunner(new ConsoleCommandRunner() {
      override def execute(commands: util.ArrayList[String], workDirectory: String): String = {
        "command not found"
      }
    })

    val isCliInstalled = snykPluginState.cliClient.isCliInstalled()

    assertFalse(isCliInstalled)
  }

  @Test
  def testIsCliInstalledSuccess(): Unit = {
    val snykPluginState = SnykPluginState.newInstance(currentProject)

    snykPluginState.cliClient.setConsoleCommandRunner(new ConsoleCommandRunner() {
      override def execute(commands: util.ArrayList[String], workDirectory: String): String = {
        "1.290.2"
      }
    })

    val isCliInstalled = snykPluginState.cliClient.isCliInstalled()

    assertTrue(isCliInstalled)
  }

  @Test
  def testPrepareProjectBeforeCliCall(): Unit = {
    val snykPluginState = SnykPluginState.newInstance(currentProject)

    val notMultiModuleProjectDependency = ProjectDependency(
      "<none>",
      "<none>",
      "<none>",
      "<none>",
      None,
      None,
      Nil,
      ProjectType.MAVEN,
      isMultiModuleProject = false
    )

    val defaultStepPrepareProjectStatus = snykPluginState
      .cliClient
      .prepareProjectBeforeCliCall(currentProject, notMultiModuleProjectDependency)

    assertEquals(PrepareProjectStatus.DEFAULT_STEP, defaultStepPrepareProjectStatus)

    val multiModuleProjectDependency = ProjectDependency(
      "<none>",
      "<none>",
      "<none>",
      "<none>",
      None,
      None,
      Nil,
      ProjectType.MAVEN,
      isMultiModuleProject = true
    )

    val multiModulePrepareProjectStatus = snykPluginState
      .cliClient
      .prepareProjectBeforeCliCall(currentProject, multiModuleProjectDependency)

    assertEquals(PrepareProjectStatus.MAVEN_INSTALL_STEP_FINISHED, multiModulePrepareProjectStatus)
  }
}
