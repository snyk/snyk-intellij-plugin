package io.snyk.plugin

import java.io.File

import org.junit.Test
import org.junit.Assert._
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.wm.impl.ToolWindowHeadlessManagerImpl
import io.snyk.plugin.client.{ApiClient, SnykConfig}
import io.snyk.plugin.datamodel.{SecurityVuln, SnykMavenArtifact}
import io.snyk.plugin.ui.SnykToolWindowFactory

import scala.io.{Codec, Source}

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
  def testRunScanForMultiModuleProject(): Unit = {
    val config = SnykConfig.default
    val apiClient = ApiClient.standard(config)

    val triedResponse = apiClient.runScan(currentProject, SnykMavenArtifact.empty)

    assertTrue(triedResponse.isSuccess)

    val snykVulnResponseSeq = triedResponse.get

    assertEquals(3, snykVulnResponseSeq.size)

    assertEquals("SNYK-JAVA-ORGCODEHAUSJACKSON-534878",
      snykVulnResponseSeq(0).vulnerabilities(0).asInstanceOf[SecurityVuln].id)
    assertEquals("SNYK-JAVA-ORGSPRINGFRAMEWORK-542935",
      snykVulnResponseSeq(1).vulnerabilities(0).asInstanceOf[SecurityVuln].id)
    assertEquals("SNYK-JAVA-ORGSPRINGFRAMEWORK-72470",
      snykVulnResponseSeq(1).vulnerabilities(1).asInstanceOf[SecurityVuln].id)
  }

  private def createModule(moduleName: String, pomXmlStr: String): Unit = {
    val webModuleDirectory = new File(currentProject.getBasePath, moduleName)
    webModuleDirectory.mkdir()

    val webModuleDirectoryVirtualFile = LocalFileSystem.getInstance.refreshAndFindFileByIoFile(webModuleDirectory)

    createPomXmlVirtualFile(webModuleDirectoryVirtualFile, pomXmlStr)
  }
}
