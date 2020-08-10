package io.snyk.plugin

import java.io.File

import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.externalSystem.model.task.{ExternalSystemTaskId, ExternalSystemTaskType}
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemProgressNotificationManager
import com.intellij.openapi.externalSystem.service.remote.RemoteExternalSystemProgressNotificationManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.wm.impl.ToolWindowHeadlessManagerImpl
import io.snyk.plugin.client.{CliClient, SnykConfig}
import io.snyk.plugin.datamodel.{ProjectDependency, SecurityVuln}
import io.snyk.plugin.depsource.ProjectType
import io.snyk.plugin.ui.MockSnykToolWindowFactory
import io.snyk.plugin.ui.settings.SnykIdeSettings
import io.snyk.plugin.ui.state.SnykPluginState
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.junit.Assert._
import org.junit.Test

import scala.io.{Codec, Source}

class SnykGradleMultiModuleTest extends AbstractGradleTestCase() {

  override protected def importProject(buildGradleStr: String): Unit = {
    createProjectConfig(buildGradleStr)

    val templateCoreBuildGradleStr = Source.fromResource("gradle-multi-module-project/template-core-build.gradle", getClass.getClassLoader)(Codec.UTF8).mkString
    createModule("template-core", templateCoreBuildGradleStr)

    val templateServerBuildGradleStr = Source.fromResource("gradle-multi-module-project/template-server-build.gradle", getClass.getClassLoader)(Codec.UTF8).mkString
    createModule("template-server", templateServerBuildGradleStr)

    doImportProject()
  }

  override protected def setUp(): Unit = {
    super.setUp()

    val settingsGradleStr = Source.fromResource("gradle-multi-module-project/settings.gradle", getClass.getClassLoader)(Codec.UTF8).mkString
    createSettingsFile(settingsGradleStr)

    val buildGradleStr = Source.fromResource("gradle-multi-module-project/build.gradle", getClass.getClassLoader)(Codec.UTF8).mkString
    importProject(buildGradleStr)

    val mockToolWindow = new ToolWindowHeadlessManagerImpl.MockToolWindow(currentProject)

    val mockToolWindowFactory = MockSnykToolWindowFactory(SnykPluginState.newInstance(currentProject))

    mockToolWindowFactory.createToolWindowContent(currentProject, mockToolWindow)
  }

  @Test
  def testRunScanForGradleMultiModuleProject(): Unit = {
    val config = SnykConfig.default
    val apiClient = CliClient.newInstance(config)

    val projectDependency = ProjectDependency(
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

    val snykVulnResponseSeqTry = apiClient.runScan(currentProject, SnykIdeSettings(), projectDependency)

    assertTrue(snykVulnResponseSeqTry.isSuccess)

    val snykVulnResponseSeq = snykVulnResponseSeqTry.get

    assertEquals(3, snykVulnResponseSeq.size)

    val vulnerabilityModuleNames = snykVulnResponseSeq
      .map(vulnerabilityObj => vulnerabilityObj.vulnerabilities.get
        .map {
          case vuln: SecurityVuln => vuln.id
          case _ =>
        })
      .flatMap(array => array.seq.map(item => item))

    assertTrue(vulnerabilityModuleNames.contains("SNYK-JAVA-COMGOOGLEGUAVA-32236"))

    assertTrue(vulnerabilityModuleNames.contains("SNYK-JAVA-ORGAPACHELOGGINGLOG4J-567761"))

    assertTrue(vulnerabilityModuleNames.contains("SNYK-JAVA-ORGAPACHELOGGINGLOG4J-567761"))

    assertTrue(vulnerabilityModuleNames.contains("SNYK-JAVA-ORGCODEHAUSJACKSON-534878"))

    assertTrue(vulnerabilityModuleNames.contains("SNYK-JAVA-COMGOOGLEGUAVA-32236"))

    assertTrue(vulnerabilityModuleNames.contains("SNYK-JAVA-ORGAPACHELOGGINGLOG4J-567761"))

    assertTrue(vulnerabilityModuleNames.contains("SNYK-JAVA-ORGAPACHELOGGINGLOG4J-567761"))

    assertTrue(vulnerabilityModuleNames.contains("SNYK-JAVA-ORGJOLOKIA-32136"))

    assertTrue(vulnerabilityModuleNames.contains("SNYK-JAVA-ORGJOLOKIA-32137"))

    assertTrue(vulnerabilityModuleNames.contains("SNYK-JAVA-ORGJOLOKIA-540501"))
  }

  @Test
  def testSnykPluginWithGradleMultiModuleProject(): Unit = {
    val notificationManager = ServiceManager.getService(classOf[ExternalSystemProgressNotificationManager])
    val externalSystemProgressNotificationManager = notificationManager.asInstanceOf[RemoteExternalSystemProgressNotificationManager]

    val taskId = ExternalSystemTaskId.create(GradleConstants.SYSTEM_ID, ExternalSystemTaskType.RESOLVE_PROJECT, currentProject)
    externalSystemProgressNotificationManager.onEnd(taskId)

    waitBackgroundTasks(60) // This is still a tiny and vulnerable part for this test.

    val snykPluginState = SnykPluginState.newInstance(currentProject)

    val snykVulnResponseSeqOption = snykPluginState.latestScanForSelectedProject

    assertTrue(snykVulnResponseSeqOption.isDefined)

    val snykVulnResponseSeq = snykVulnResponseSeqOption.get

    assertEquals(3, snykVulnResponseSeq.size)

    val vulnerabilityModuleNames = snykVulnResponseSeq
      .map(vulnerabilityObj => vulnerabilityObj.vulnerabilities.get
        .map {
          case vuln: SecurityVuln => vuln.id
          case _ =>
        })
      .flatMap(array => array.seq.map(item => item))

    assertTrue(vulnerabilityModuleNames.contains("SNYK-JAVA-COMGOOGLEGUAVA-32236"))

    assertTrue(vulnerabilityModuleNames.contains("SNYK-JAVA-ORGAPACHELOGGINGLOG4J-567761"))

    assertTrue(vulnerabilityModuleNames.contains("SNYK-JAVA-ORGAPACHELOGGINGLOG4J-567761"))

    assertTrue(vulnerabilityModuleNames.contains("SNYK-JAVA-ORGCODEHAUSJACKSON-534878"))

    assertTrue(vulnerabilityModuleNames.contains("SNYK-JAVA-COMGOOGLEGUAVA-32236"))

    assertTrue(vulnerabilityModuleNames.contains("SNYK-JAVA-ORGAPACHELOGGINGLOG4J-567761"))

    assertTrue(vulnerabilityModuleNames.contains("SNYK-JAVA-ORGAPACHELOGGINGLOG4J-567761"))

    assertTrue(vulnerabilityModuleNames.contains("SNYK-JAVA-ORGJOLOKIA-32136"))

    assertTrue(vulnerabilityModuleNames.contains("SNYK-JAVA-ORGJOLOKIA-32137"))

    assertTrue(vulnerabilityModuleNames.contains("SNYK-JAVA-ORGJOLOKIA-540501"))
  }

  private def createModule(moduleName: String, buildGradleStr: String): Unit = {
    val moduleDirectory = new File(currentProject.getBasePath, moduleName)
    moduleDirectory.mkdir()

    val moduleDirectoryVirtualFile = LocalFileSystem.getInstance.refreshAndFindFileByIoFile(moduleDirectory)

    createConfigFile(moduleDirectoryVirtualFile, buildGradleStr)
  }
}
