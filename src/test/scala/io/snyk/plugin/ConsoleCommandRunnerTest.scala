package io.snyk.plugin

import java.io.File

import com.intellij.openapi.wm.impl.ToolWindowHeadlessManagerImpl
import io.snyk.plugin.client.ConsoleCommandRunner
import io.snyk.plugin.ui.MockSnykToolWindowFactory
import io.snyk.plugin.ui.state.SnykPluginState
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.junit.Test
import org.junit.Assert.{assertEquals, assertFalse, assertTrue, fail}

import scala.io.{Codec, Source}

class ConsoleCommandRunnerTest extends AbstractMavenTestCase() {

  override protected def setUp(): Unit = {
    super.setUp()

    val mockToolWindow = new ToolWindowHeadlessManagerImpl.MockToolWindow(currentProject)

    val mockToolWindowFactory = MockSnykToolWindowFactory(SnykPluginState.newInstance(currentProject))

    mockToolWindowFactory.createToolWindowContent(currentProject, mockToolWindow)
  }

  @Test
  def testRunInstall(): Unit = {
    setupTestProject("sample-pom.xml")

    val repositoryFile = MavenProjectsManager
      .getInstance(currentProject).getGeneralSettings.getEffectiveLocalRepository

    val sampleArtifactIdSnapshotPath =
      s"${repositoryFile.getAbsolutePath}/sampleProjectGroupdId/sampleArtifactId/1.0-SNAPSHOT/sampleArtifactId-1.0-SNAPSHOT.jar"

    val sampleArtifactIdSnapshotFile = new File(sampleArtifactIdSnapshotPath)

    if (sampleArtifactIdSnapshotFile.exists()) {
      sampleArtifactIdSnapshotFile.delete()
    }

    ConsoleCommandRunner().runMavenInstall(currentProject)

    waitBackgroundTasks(60)

    assertTrue(sampleArtifactIdSnapshotFile.exists())

    sampleArtifactIdSnapshotFile.delete()
  }

  @Test
  def testRunInstallWithError(): Unit = {
    setupTestProject("sample-pom-with-unavailable-dependency.xml")

    val repositoryFile = MavenProjectsManager
      .getInstance(currentProject).getGeneralSettings.getEffectiveLocalRepository

    val sampleArtifactIdSnapshotPath =
      s"${repositoryFile.getAbsolutePath}/sampleProjectGroupdId/sampleArtifactId/1.0-SNAPSHOT/sampleArtifactId-1.0-SNAPSHOT.jar"

    val sampleArtifactIdSnapshotFile = new File(sampleArtifactIdSnapshotPath)

    if (sampleArtifactIdSnapshotFile.exists()) {
      sampleArtifactIdSnapshotFile.delete()
    }

    val consoleCommandRunner = ConsoleCommandRunner()

    consoleCommandRunner.runMavenInstall(currentProject)

    waitBackgroundTasks(60)

    assertFalse(sampleArtifactIdSnapshotFile.exists())

    assertEquals("[mnv install] failed with exit code 1", consoleCommandRunner.terminationMessage())
  }

  private def setupTestProject(projectXmlFileName: String): Unit = {
    val projectXmlStr = Source
      .fromResource(projectXmlFileName, getClass.getClassLoader)(Codec.UTF8).mkString

    importProject(projectXmlStr)

    setupJdkForAllModules()
  }
}
