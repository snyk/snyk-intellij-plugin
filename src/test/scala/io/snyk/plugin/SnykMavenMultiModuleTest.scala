package io.snyk.plugin

import java.io.File

import org.junit.Ignore
import org.junit.Test
import org.junit.Assert._
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.wm.impl.ToolWindowHeadlessManagerImpl
import io.snyk.plugin.client.{CliClient, ConsoleCommandRunner, SnykConfig}
import io.snyk.plugin.datamodel.{ProjectDependency, SecurityVuln}
import io.snyk.plugin.depsource.ProjectType
import io.snyk.plugin.ui.SnykToolWindowFactory
import java.{util => ju}

import com.intellij.openapi.project.Project
import io.snyk.plugin.ui.state.SnykPluginState
import org.jetbrains.idea.maven.utils.MavenProgressIndicator

import scala.io.{Codec, Source}

@Ignore
class SnykMavenMultiModuleTest extends AbstractMavenTestCase() {

  override protected def setUp(): Unit = {
    super.setUp()

    val projectXmlStr = Source.fromResource("sample-multi-module-pom.xml", getClass.getClassLoader)(Codec.UTF8).mkString

    importProject(projectXmlStr)

    createModule("core", Source.fromResource("sample-multi-module-core-pom.xml", getClass.getClassLoader)(Codec.UTF8).mkString)
    createModule("web", Source.fromResource("sample-multi-module-web-pom.xml", getClass.getClassLoader)(Codec.UTF8).mkString)

    val mockToolWindow = new ToolWindowHeadlessManagerImpl.MockToolWindow(currentProject)

    val snykToolWindowFactory = new SnykToolWindowFactory

    snykToolWindowFactory.createToolWindowContent(currentProject, mockToolWindow)
  }

  @Test
  def testRunScanForMavenMultiModuleProject(): Unit = {
    val config = SnykConfig.default
    val apiClient = CliClient.newInstance(config)

    val mavenArtifact = ProjectDependency(
      "<none>",
      "<none>",
      "<none>",
      "<none>",
      None,
      None,
      Nil,
      ProjectType.MAVEN,
      true
    )

    val snykVulnResponseSeqTry = apiClient.runScan(currentProject, mavenArtifact)

    assertTrue(snykVulnResponseSeqTry.isSuccess)

    val snykVulnResponseSeq = snykVulnResponseSeqTry.get

    assertEquals(3, snykVulnResponseSeq.size)

    assertEquals("SNYK-JAVA-ORGCODEHAUSJACKSON-534878",
      snykVulnResponseSeq(0).vulnerabilities.get(0).asInstanceOf[SecurityVuln].id)
    assertEquals("SNYK-JAVA-ORGSPRINGFRAMEWORK-559346",
      snykVulnResponseSeq(1).vulnerabilities.get(0).asInstanceOf[SecurityVuln].id)
    assertEquals("SNYK-JAVA-ORGSPRINGFRAMEWORK-72470",
      snykVulnResponseSeq(1).vulnerabilities.get(1).asInstanceOf[SecurityVuln].id)
  }

  @Test
  def testSnykPluginWithMavenMultiModuleProject(): Unit = {
    SnykPluginState.getInstance(currentProject).cliClient.setConsoleCommandRunner(new ConsoleCommandRunner() {
      override def runMavenInstallGoal(project: Project): Unit = {
      }
    })

    myProjectsTree
      .update(ju.Arrays.asList({projectPomVirtualFile}), true, myProjectsManager.getGeneralSettings, new MavenProgressIndicator())

    waitBackgroundTasks(60) // This is still a tiny and vulnerable part for this test.

    val snykPluginState = SnykPluginState.newInstance(currentProject)

    val snykVulnResponseSeqOption = snykPluginState.latestScanForSelectedProject

    assertTrue(snykVulnResponseSeqOption.isDefined)

    val snykVulnResponseSeq = snykVulnResponseSeqOption.get

    assertEquals(3, snykVulnResponseSeq.size)

    assertEquals("SNYK-JAVA-ORGCODEHAUSJACKSON-534878",
      snykVulnResponseSeq(0).vulnerabilities.get(0).asInstanceOf[SecurityVuln].id)
    assertEquals("SNYK-JAVA-ORGSPRINGFRAMEWORK-559346",
      snykVulnResponseSeq(1).vulnerabilities.get(0).asInstanceOf[SecurityVuln].id)
    assertEquals("SNYK-JAVA-ORGSPRINGFRAMEWORK-72470",
      snykVulnResponseSeq(1).vulnerabilities.get(1).asInstanceOf[SecurityVuln].id)
  }

  private def createModule(moduleName: String, pomXmlStr: String): Unit = {
    val moduleDirectory = new File(currentProject.getBasePath, moduleName)
    moduleDirectory.mkdir()

    val moduleDirectoryVirtualFile = LocalFileSystem.getInstance.refreshAndFindFileByIoFile(moduleDirectory)

    createPomXmlVirtualFile(moduleDirectoryVirtualFile, pomXmlStr)
  }
}
