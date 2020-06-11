package io.snyk.plugin

import java.io.File
import java.util

import io.snyk.plugin.client.{CliClient, ConsoleCommandRunner, Platform, PrepareProjectStatus, SnykConfig}
import io.snyk.plugin.datamodel.{ProjectDependency, SecurityVuln}
import io.snyk.plugin.depsource.ProjectType
import io.snyk.plugin.ui.settings.SnykIntelliJSettings
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

    val projectDependency = ProjectDependency(
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

    val snykVulnResponse = snykPluginState.cliClient.runScan(currentProject, SnykIntelliJSettings.Empty, projectDependency)

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
  def testIsCliInstalledAutomaticallyByPluginFailed(): Unit = {
    SnykPluginState.removeForProject(currentProject)

    val snykPluginState = SnykPluginState.newInstance(currentProject)

    snykPluginState.cliClient.setConsoleCommandRunner(new ConsoleCommandRunner() {
      override def execute(commands: util.ArrayList[String], workDirectory: String): String = {
        "command not found"
      }
    })

    val cliFile = new File(snykPluginState.pluginPath, Platform.current.snykWrapperFileName)

    if (cliFile.exists()) {
      cliFile.delete()
    }

    val isCliInstalled = snykPluginState.cliClient.isCliInstalled()

    assertFalse(isCliInstalled)
  }

  @Test
  def testIsCliInstalledAutomaticallyByPluginSuccess(): Unit = {
    SnykPluginState.removeForProject(currentProject)

    val snykPluginState = SnykPluginState.newInstance(currentProject)

    snykPluginState.cliClient.setConsoleCommandRunner(new ConsoleCommandRunner() {
      override def execute(commands: util.ArrayList[String], workDirectory: String): String = {
        "command not found"
      }
    })

    val cliFile = new File(snykPluginState.pluginPath, Platform.current.snykWrapperFileName)

    if (!cliFile.exists()) {
      cliFile.createNewFile()
    }

    val isCliInstalled = snykPluginState.cliClient.isCliInstalled()

    assertTrue(isCliInstalled)

    cliFile.delete()
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

  @Test
  def testBuildCliCommandsListForMaven(): Unit = {
    val cliClient = CliClient.newInstance(SnykConfig.default)

    val mavenDependency = ProjectDependency(
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

    val emptySettings = new SnykIntelliJSettings {
      override def getCustomEndpointUrl(): String = ""

      override def getOrganization(): String = ""

      override def isIgnoreUnknownCA(): Boolean = false
    }

    val defaultCommands = cliClient.buildCliCommandsList(emptySettings, mavenDependency)

    assertEquals("snyk", defaultCommands.get(0))
    assertEquals("--json", defaultCommands.get(1))
    assertEquals("--all-projects", defaultCommands.get(2))
    assertEquals("test", defaultCommands.get(3))
  }

  @Test
  def testBuildCliCommandsListForGradle(): Unit = {
    val cliClient = CliClient.newInstance(SnykConfig.default)

    val gradleDependency = ProjectDependency(
      "<none>",
      "<none>",
      "<none>",
      "<none>",
      None,
      None,
      Nil,
      ProjectType.GRADLE,
      isMultiModuleProject = false
      )

    val emptySettings = new SnykIntelliJSettings {
      override def getCustomEndpointUrl(): String = ""

      override def getOrganization(): String = ""

      override def isIgnoreUnknownCA(): Boolean = false
    }

    val defaultCommands = cliClient.buildCliCommandsList(emptySettings, gradleDependency)

    assertEquals("snyk", defaultCommands.get(0))
    assertEquals("--json", defaultCommands.get(1))
    assertEquals("--all-sub-projects", defaultCommands.get(2))
    assertEquals("test", defaultCommands.get(3))
  }

  @Test
  def testBuildCliCommandsListWithCustomEndpointParameter(): Unit = {
    val cliClient = CliClient.newInstance(SnykConfig.default)

    val gradleDependency = ProjectDependency(
      "<none>",
      "<none>",
      "<none>",
      "<none>",
      None,
      None,
      Nil,
      ProjectType.GRADLE,
      isMultiModuleProject = false
      )

    val customEndpointSettings = new SnykIntelliJSettings {
      override def getCustomEndpointUrl(): String = "https://app.snyk.io/api"

      override def getOrganization(): String = ""

      override def isIgnoreUnknownCA(): Boolean = false
    }

    val defaultCommands = cliClient.buildCliCommandsList(customEndpointSettings, gradleDependency)

    assertEquals("snyk", defaultCommands.get(0))
    assertEquals("--json", defaultCommands.get(1))
    assertEquals("--api=https://app.snyk.io/api", defaultCommands.get(2))
    assertEquals("--all-sub-projects", defaultCommands.get(3))
    assertEquals("test", defaultCommands.get(4))
  }

  @Test
  def testBuildCliCommandsListWithInsecureParameter(): Unit = {
    val cliClient = CliClient.newInstance(SnykConfig.default)

    val gradleDependency = ProjectDependency(
      "<none>",
      "<none>",
      "<none>",
      "<none>",
      None,
      None,
      Nil,
      ProjectType.GRADLE,
      isMultiModuleProject = false
      )

    val insecureSettings = new SnykIntelliJSettings {
      override def getCustomEndpointUrl(): String = ""

      override def getOrganization(): String = ""

      override def isIgnoreUnknownCA(): Boolean = true
    }

    val defaultCommands = cliClient.buildCliCommandsList(insecureSettings, gradleDependency)

    assertEquals("snyk", defaultCommands.get(0))
    assertEquals("--json", defaultCommands.get(1))
    assertEquals("--insecure", defaultCommands.get(2))
    assertEquals("--all-sub-projects", defaultCommands.get(3))
    assertEquals("test", defaultCommands.get(4))
  }

  @Test
  def testBuildCliCommandsListWithOrganizationParameter(): Unit = {
    val cliClient = CliClient.newInstance(SnykConfig.default)

    val gradleDependency = ProjectDependency(
      "<none>",
      "<none>",
      "<none>",
      "<none>",
      None,
      None,
      Nil,
      ProjectType.GRADLE,
      isMultiModuleProject = false
      )

    val organizationSettings = new SnykIntelliJSettings {
      override def getCustomEndpointUrl(): String = ""

      override def getOrganization(): String = "test-org"

      override def isIgnoreUnknownCA(): Boolean = false
    }

    val defaultCommands = cliClient.buildCliCommandsList(organizationSettings, gradleDependency)

    assertEquals("snyk", defaultCommands.get(0))
    assertEquals("--json", defaultCommands.get(1))
    assertEquals("--org=test-org", defaultCommands.get(2))
    assertEquals("--all-sub-projects", defaultCommands.get(3))
    assertEquals("test", defaultCommands.get(4))
  }

  @Test
  def testBuildCliCommandsListWithAllParameter(): Unit = {
    val cliClient = CliClient.newInstance(SnykConfig.default)

    val gradleDependency = ProjectDependency(
      "<none>",
      "<none>",
      "<none>",
      "<none>",
      None,
      None,
      Nil,
      ProjectType.GRADLE,
      isMultiModuleProject = false
      )

    val allSettings = new SnykIntelliJSettings {
      override def getCustomEndpointUrl(): String = "https://app.snyk.io/api"

      override def getOrganization(): String = "test-org"

      override def isIgnoreUnknownCA(): Boolean = true
    }

    val defaultCommands = cliClient.buildCliCommandsList(allSettings, gradleDependency)

    assertEquals("snyk", defaultCommands.get(0))
    assertEquals("--json", defaultCommands.get(1))
    assertEquals("--api=https://app.snyk.io/api", defaultCommands.get(2))
    assertEquals("--insecure", defaultCommands.get(3))
    assertEquals("--org=test-org", defaultCommands.get(4))
    assertEquals("--all-sub-projects", defaultCommands.get(5))
    assertEquals("test", defaultCommands.get(6))
  }

  @Test
  def testRunScanWithInsecureParameter(): Unit = {
    val snykPluginState = SnykPluginState.newInstance(currentProject)

    val projectDependency = ProjectDependency(
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

    val allSettings = new SnykIntelliJSettings {
      override def getCustomEndpointUrl(): String = ""

      override def getOrganization(): String = ""

      override def isIgnoreUnknownCA(): Boolean = true
    }

    val snykVulnResponse = snykPluginState.cliClient.runScan(currentProject, allSettings, projectDependency)

    assertTrue(snykVulnResponse.isSuccess)

    val vulnerabilities = snykVulnResponse.get.head.vulnerabilities

    assertEquals("One vulnerability expected", 1, vulnerabilities.size)
    assertEquals("org.codehaus.jackson:jackson-mapper-asl",
                 vulnerabilities.head.head.asInstanceOf[SecurityVuln].moduleName)
  }
}
