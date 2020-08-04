package io.snyk.plugin

import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.externalSystem.model.task.{ExternalSystemTaskId, ExternalSystemTaskType}
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemProgressNotificationManager
import com.intellij.openapi.externalSystem.service.remote.RemoteExternalSystemProgressNotificationManager
import com.intellij.openapi.wm.impl.ToolWindowHeadlessManagerImpl
import io.snyk.plugin.datamodel.SecurityVuln
import io.snyk.plugin.ui.MockSnykToolWindowFactory
import io.snyk.plugin.ui.state.SnykPluginState
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.junit.Assert.{assertEquals, assertFalse, assertTrue}
import org.junit.Test

import scala.io.{Codec, Source}

class SnykGradleProjectPluginTest extends AbstractGradleTestCase() {

  override protected def setUp(): Unit = {
    super.setUp()

    createSettingsFile("include 'api', 'impl' ")

    importProject(Source.fromResource("sample-gradle.build", getClass.getClassLoader)(Codec.UTF8).mkString)

    val mockToolWindow = new ToolWindowHeadlessManagerImpl.MockToolWindow(currentProject)

    val mockToolWindowFactory = MockSnykToolWindowFactory(SnykPluginState.newInstance(currentProject))

    mockToolWindowFactory.createToolWindowContent(currentProject, mockToolWindow)
  }

  @Test
  def testSingleModuleGradleGetVulnerabilities(): Unit = {
    val notificationManager = ServiceManager.getService(classOf[ExternalSystemProgressNotificationManager])
    val externalSystemProgressNotificationManager = notificationManager.asInstanceOf[RemoteExternalSystemProgressNotificationManager]

    val taskId = ExternalSystemTaskId.create(GradleConstants.SYSTEM_ID, ExternalSystemTaskType.RESOLVE_PROJECT, currentProject)
    externalSystemProgressNotificationManager.onEnd(taskId)

    val snykPluginState = SnykPluginState.newInstance(currentProject)

    waitBackgroundTasks(60) // This is still a tiny and vulnerable part for this test.

    assertFalse(snykPluginState.latestScanForSelectedProject.isEmpty)

    assertEquals("gradle", snykPluginState.latestScanForSelectedProject.get.head.packageManager.get)

    val snykVulnResponseSeq = snykPluginState.latestScanForSelectedProject.get

    val vulnerabilityTitles = snykVulnResponseSeq
      .map(vulnerabilityObj => vulnerabilityObj.vulnerabilities.get
        .map {
          case vuln: SecurityVuln => vuln.title
          case _ =>
        })
      .flatMap(array => array.seq.map(item => item))

    assertTrue(vulnerabilityTitles.contains("Cross-site Scripting (XSS)"))
    assertTrue(vulnerabilityTitles.contains("Arbitrary Code Execution"))
    assertTrue(vulnerabilityTitles.contains("Cross-Site Request Forgery (CSRF)"))
    assertTrue(vulnerabilityTitles.contains("XML External Entity (XXE) Injection"))

    val vulnerabilityModuleNames = snykVulnResponseSeq
      .map(vulnerabilityObj => vulnerabilityObj.vulnerabilities.get
        .map {
          case vuln: SecurityVuln => vuln.moduleName
          case _ =>
        })
      .flatMap(array => array.seq.map(item => item))

    assertTrue(vulnerabilityModuleNames.contains("org.jolokia:jolokia-core"))
    assertTrue(vulnerabilityModuleNames.contains("org.codehaus.jackson:jackson-mapper-asl"))
  }
}
