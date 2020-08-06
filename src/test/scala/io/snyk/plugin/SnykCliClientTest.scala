package io.snyk.plugin

import java.io.File
import java.util

import io.snyk.plugin.client.{CliClient, ConsoleCommandRunner, Platform, PrepareProjectStatus, SnykConfig}
import io.snyk.plugin.datamodel.ProjectDependency
import io.snyk.plugin.depsource.ProjectType
import io.snyk.plugin.ui.settings.SnykPersistentStateComponent
import io.snyk.plugin.ui.state.SnykPluginState
import org.junit.Assert.{assertEquals, assertFalse, assertNotNull, assertTrue}
import org.junit.Test

import scala.io.{Codec, Source}

class SnykCliClientTest extends AbstractMavenTestCase() {

  override protected def setUp(): Unit = {
    super.setUp()

    val projectXmlStr = Source.fromResource("sample-pom.xml", getClass.getClassLoader)(Codec.UTF8).mkString

    importProject(projectXmlStr)

    setupJdkForAllModules()
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

    val snykVulnResponse = snykPluginState.cliClient.runScan(currentProject, SnykPersistentStateComponent(), projectDependency)

    assertTrue(snykVulnResponse.isSuccess)

    val vulnerabilities = snykVulnResponse.get.head.vulnerabilities

    assertTrue(vulnerabilities.isDefined)
    assertEquals("One vulnerability expected", 1, vulnerabilities.size)

    val vulnerabilityModuleNames = vulnerabilities.get.map(vulnerability => vulnerability.name)

    assertTrue(vulnerabilityModuleNames.contains("org.codehaus.jackson:jackson-mapper-asl"))
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

    val defaultCommands = cliClient.buildCliCommandsList(SnykPersistentStateComponent(), mavenDependency)

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

    val defaultCommands = cliClient.buildCliCommandsList(SnykPersistentStateComponent(), gradleDependency)

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

    val defaultCommands = cliClient
      .buildCliCommandsList(SnykPersistentStateComponent(customEndpointUrl = "https://app.snyk.io/api"), gradleDependency)

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

    val defaultCommands = cliClient
      .buildCliCommandsList(SnykPersistentStateComponent(isIgnoreUnknownCA = true), gradleDependency)

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

    val defaultCommands = cliClient
      .buildCliCommandsList(SnykPersistentStateComponent(organization = "test-org"), gradleDependency)

    assertEquals("snyk", defaultCommands.get(0))
    assertEquals("--json", defaultCommands.get(1))
    assertEquals("--org=test-org", defaultCommands.get(2))
    assertEquals("--all-sub-projects", defaultCommands.get(3))
    assertEquals("test", defaultCommands.get(4))
  }

  @Test
  def testBuildCliCommandsListWithFileParameter(): Unit = {
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

    val defaultCommands = cliClient
      .buildCliCommandsList(SnykPersistentStateComponent(additionalParameters = "--file=build.gradle"), gradleDependency)

    assertEquals("snyk", defaultCommands.get(0))
    assertEquals("--json", defaultCommands.get(1))
    assertEquals("--file=build.gradle", defaultCommands.get(2))
    assertEquals("test", defaultCommands.get(3))
  }

  @Test
  def testBuildCliCommandsListForMavenProjectWithFileParameter(): Unit = {
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

    val defaultCommands = cliClient
      .buildCliCommandsList(SnykPersistentStateComponent(additionalParameters = "--file=pom.xml"), mavenDependency)

    assertEquals("snyk", defaultCommands.get(0))
    assertEquals("--json", defaultCommands.get(1))
    assertEquals("--file=pom.xml", defaultCommands.get(2))
    assertEquals("test", defaultCommands.get(3))
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

    val allSettings = SnykPersistentStateComponent(
      customEndpointUrl = "https://app.snyk.io/api",
      organization = "test-org",
      isIgnoreUnknownCA = true,
      additionalParameters = "--file=build.gradle")

    val defaultCommands = cliClient.buildCliCommandsList(allSettings, gradleDependency)

    assertEquals("snyk", defaultCommands.get(0))
    assertEquals("--json", defaultCommands.get(1))
    assertEquals("--api=https://app.snyk.io/api", defaultCommands.get(2))
    assertEquals("--insecure", defaultCommands.get(3))
    assertEquals("--org=test-org", defaultCommands.get(4))
    assertEquals("--file=build.gradle", defaultCommands.get(5))
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

    val snykVulnResponse = snykPluginState
      .cliClient
      .runScan(currentProject, SnykPersistentStateComponent(isIgnoreUnknownCA = true), projectDependency)

    assertTrue(snykVulnResponse.isSuccess)

    val vulnerabilities = snykVulnResponse.get.head.vulnerabilities

    assertTrue(vulnerabilities.isDefined)

    val vulnerabilityModuleNames = vulnerabilities.get.map(vulnerability => vulnerability.name)

    assertEquals("One vulnerability expected", 1, vulnerabilities.size)
    assertTrue(vulnerabilityModuleNames.contains("org.codehaus.jackson:jackson-mapper-asl"))
  }

  @Test
  def testRunScanForProjectWithMavenAndNpm(): Unit = {
    val packageJsonFile = new File(currentProject.getBasePath, "package.json")

    packageJsonFile.createNewFile()

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

    val snykVulnResponse = snykPluginState
      .cliClient
      .runScan(currentProject, SnykPersistentStateComponent(additionalParameters = "--file=pom.xml"), projectDependency)

    assertTrue(snykVulnResponse.isSuccess)

    val vulnerabilities = snykVulnResponse.get.head.vulnerabilities

    assertTrue(vulnerabilities.isDefined)

    val vulnerabilityModuleNames = vulnerabilities.get.map(vulnerability => vulnerability.name)

    assertEquals("One vulnerability expected", 1, vulnerabilities.size)
    assertTrue(vulnerabilityModuleNames.contains("org.codehaus.jackson:jackson-mapper-asl"))

    packageJsonFile.delete()
  }
}
