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
import org.junit.Ignore
import org.junit.Test

import scala.io.{Codec, Source}

class SnykGradleProjectPluginTest extends AbstractGradleTestCase() {

  override protected def setUp(): Unit = {
    super.setUp()

    createSettingsFile("include 'api', 'impl' ")

    importProject(Source.fromResource("sample-gradle.build", getClass.getClassLoader)(Codec.UTF8).mkString)

    val mockToolWindow = new ToolWindowHeadlessManagerImpl.MockToolWindow(currentProject)

    val mockToolWindowFactory = MockSnykToolWindowFactory(SnykPluginState.forIntelliJ(currentProject))

    mockToolWindowFactory.createToolWindowContent(currentProject, mockToolWindow)
  }

  @Test
  def testSingleModuleGradleGetVulnerabilities(): Unit = {
    val notificationManager = ServiceManager.getService(classOf[ExternalSystemProgressNotificationManager])
    val externalSystemProgressNotificationManager = notificationManager.asInstanceOf[RemoteExternalSystemProgressNotificationManager]

    val taskId = ExternalSystemTaskId.create(GradleConstants.SYSTEM_ID, ExternalSystemTaskType.RESOLVE_PROJECT, currentProject)
    externalSystemProgressNotificationManager.onEnd(taskId)

    val snykPluginState = SnykPluginState.newInstance(currentProject)

    waitBackgroundTasks(30) // This is still a tiny and vulnerable part for this test.

    assertFalse(snykPluginState.latestScanForSelectedProject.isEmpty)

    assertEquals("gradle", snykPluginState.latestScanForSelectedProject.get.head.packageManager.get)

    val vulnerabilities = snykPluginState.latestScanForSelectedProject.get.head.vulnerabilities.get

    assertEquals("One vulnerability expected", 4, vulnerabilities.size)

    val expectedVulnerabilityTitles = Seq("Cross-site Scripting (XSS)",
      "Arbitrary Code Execution",
      "Cross-Site Request Forgery (CSRF)",
      "XML External Entity (XXE) Injection")

    val expectedVulnerabilityModuleNames = Seq("org.jolokia:jolokia-core", "org.codehaus.jackson:jackson-mapper-asl")

    vulnerabilities.foreach(vulnerability => {
      assertTrue(expectedVulnerabilityTitles.contains(vulnerability.asInstanceOf[SecurityVuln].title))

      assertTrue(expectedVulnerabilityModuleNames.contains(vulnerability.asInstanceOf[SecurityVuln].moduleName))
    })
  }
}
