package io.snyk.plugin

import io.snyk.plugin.ui.state.SnykPluginState
import org.junit.Ignore
import org.junit.Test
import org.junit.Assert._
import com.intellij.openapi.wm.impl.ToolWindowHeadlessManagerImpl
import io.snyk.plugin.datamodel.SecurityVuln
import io.snyk.plugin.ui.MockSnykToolWindowFactory
import org.jetbrains.idea.maven.project.MavenProjectsManager

import scala.io.{Codec, Source}

@Ignore
class SnykMavenProjectPluginTest extends AbstractMavenTestCase() {

  override protected def setUp(): Unit = {
    super.setUp()

    val projectXmlStr = Source.fromResource("sample-pom.xml", getClass.getClassLoader)(Codec.UTF8).mkString

    importProject(projectXmlStr)

    val mockToolWindow = new ToolWindowHeadlessManagerImpl.MockToolWindow(currentProject)

    val mockToolWindowFactory = MockSnykToolWindowFactory(SnykPluginState.forIntelliJ(currentProject))

    mockToolWindowFactory.createToolWindowContent(currentProject, mockToolWindow)
  }

  @Test
  def testSingleModuleMavenGetVulnerabilities(): Unit = {
    MavenProjectsManager.getInstance(currentProject).scheduleImportAndResolve()

    val snykPluginState = SnykPluginState.newInstance(currentProject)

    waitBackgroundTasks(30) // This is still a tiny and vulnerable part for this test.

    assertFalse(snykPluginState.latestScanForSelectedProject.isEmpty)
    assertEquals("maven", snykPluginState.latestScanForSelectedProject.get.head.packageManager.get)

    val vulnerabilities = snykPluginState.latestScanForSelectedProject.get.head.vulnerabilities.get

    assertEquals("One vulnerability expected", 1, vulnerabilities.size)
    assertEquals("org.codehaus.jackson:jackson-mapper-asl",
      vulnerabilities.head.asInstanceOf[SecurityVuln].moduleName)
  }
}
