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

class SnykBasicPluginTest extends AbstractMavenTestCase() {

  override protected def setUp(): Unit = {
    super.setUp()

    val projectXmlStr = Source.fromResource("sample-pom.xml", getClass.getClassLoader)(Codec.UTF8).mkString

    importProject(projectXmlStr)

    val mockToolWindow = new ToolWindowHeadlessManagerImpl.MockToolWindow(myProject)

    val snykToolWindowFactory = new SnykToolWindowFactory

    snykToolWindowFactory.createToolWindowContent(myProject, mockToolWindow)
  }

  @Test
  def testImportMavenProject(): Unit = {
    myProjectsTree
      .update(ju.Arrays.asList({myProjectPom}), true, myProjectsManager.getGeneralSettings, new MavenProgressIndicator())

    waitBackgroundTasks()

    val snykPluginState = SnykPluginState.forIntelliJ(myProject)

    assertFalse(snykPluginState.latestScanForSelectedProject.isEmpty)

    val vulnerabilities = snykPluginState.latestScanForSelectedProject.get.vulnerabilities

    assertEquals("One vulnerability expected", 1, vulnerabilities.size)
    assertEquals("org.codehaus.jackson:jackson-mapper-asl",
                          vulnerabilities.head.asInstanceOf[SecurityVuln].moduleName)
  }

  private[this] def waitBackgroundTasks(): Unit = Thread.sleep(6000)
}
