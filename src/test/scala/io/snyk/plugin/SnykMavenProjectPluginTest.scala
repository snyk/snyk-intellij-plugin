package io.snyk.plugin

import io.snyk.plugin.ui.state.SnykPluginState
import org.junit.Test
import org.junit.Assert._
import com.intellij.openapi.wm.impl.ToolWindowHeadlessManagerImpl
import io.snyk.plugin.ui.MockSnykToolWindowFactory
import org.jetbrains.idea.maven.project.MavenProjectsManager

import scala.io.{Codec, Source}

class SnykMavenProjectPluginTest extends AbstractMavenTestCase() {

  override protected def setUp(): Unit = {
    super.setUp()

    val projectXmlStr = Source.fromResource("sample-pom.xml", getClass.getClassLoader)(Codec.UTF8).mkString

    importProject(projectXmlStr)

    val mockToolWindow = new ToolWindowHeadlessManagerImpl.MockToolWindow(currentProject)

    val mockToolWindowFactory = MockSnykToolWindowFactory(SnykPluginState.newInstance(currentProject))

    mockToolWindowFactory.createToolWindowContent(currentProject, mockToolWindow)
  }

  @Test
  def testSingleModuleMavenGetVulnerabilities(): Unit = {
    MavenProjectsManager.getInstance(currentProject).scheduleImportAndResolve()

    val snykPluginState = SnykPluginState.newInstance(currentProject)

    waitBackgroundTasks(60) // This is still a tiny and vulnerable part for this test.

    assertFalse(snykPluginState.latestScanForSelectedProject.isEmpty)
    assertEquals("maven", snykPluginState.latestScanForSelectedProject.get.head.packageManager.get)

    val vulnerabilityModuleNames = snykPluginState
      .latestScanForSelectedProject.get
      .map(snykVulnResponse => snykVulnResponse.vulnerabilities
      .map(vulnerabilitySeq => vulnerabilitySeq.seq.map(vuln => vuln.name)))
      .flatMap(array => array.seq.map(item => item))
      .flatMap(array => array.seq.map(item => item))

    assertTrue(vulnerabilityModuleNames.contains("org.codehaus.jackson:jackson-mapper-asl"))
  }
}
