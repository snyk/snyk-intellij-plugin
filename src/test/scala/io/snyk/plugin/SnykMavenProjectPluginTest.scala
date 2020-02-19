package io.snyk.plugin

import io.snyk.plugin.ui.state.SnykPluginState
import org.jetbrains.idea.maven.utils.MavenProgressIndicator
import org.junit.Test
import org.junit.Assert._
import java.{util => ju}

import com.intellij.openapi.wm.impl.ToolWindowHeadlessManagerImpl
import io.snyk.plugin.datamodel.SecurityVuln
import io.snyk.plugin.ui.SnykToolWindowFactory

import scala.io.{Codec, Source}

class SnykMavenProjectPluginTest extends AbstractMavenTestCase() {

  override protected def setUp(): Unit = {
    super.setUp()

    val projectXmlStr = Source.fromResource("sample-pom.xml", getClass.getClassLoader)(Codec.UTF8).mkString

    importProject(projectXmlStr)

    val mockToolWindow = new ToolWindowHeadlessManagerImpl.MockToolWindow(currentProject)

    val snykToolWindowFactory = new SnykToolWindowFactory

    snykToolWindowFactory.createToolWindowContent(currentProject, mockToolWindow)
  }

  @Test
  def testGetVulnerabilities(): Unit = {
    myProjectsTree
      .update(ju.Arrays.asList({projectPomVirtualFile}), true, myProjectsManager.getGeneralSettings, new MavenProgressIndicator())

    waitBackgroundTasks(20) // This is still a tiny and vulnerable part for this test.

    val snykPluginState = SnykPluginState.forIntelliJ(currentProject)

    assertFalse(snykPluginState.latestScanForSelectedProject.isEmpty)

    val vulnerabilities = snykPluginState.latestScanForSelectedProject.get.head.vulnerabilities

    assertEquals("maven", snykPluginState.latestScanForSelectedProject.get.head.packageManager)

    assertEquals("One vulnerability expected", 1, vulnerabilities.size)
    assertEquals("org.codehaus.jackson:jackson-mapper-asl",
      vulnerabilities.head.asInstanceOf[SecurityVuln].moduleName)
  }
}
