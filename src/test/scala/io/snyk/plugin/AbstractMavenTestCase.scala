package io.snyk.plugin

import java.io.{File, IOException}
import java.util.Collections
import java.util.concurrent.TimeUnit

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.{LocalFileSystem, VirtualFile}
import com.intellij.testFramework.{EdtTestUtil, RunAll}
import com.intellij.util.ui.UIUtil
import org.intellij.lang.annotations.Language
import org.jetbrains.idea.maven.indices.MavenIndicesManager
import org.jetbrains.idea.maven.model.MavenExplicitProfiles
import org.jetbrains.idea.maven.project.{MavenArtifactDownloader, MavenProjectsManager, MavenProjectsTree, MavenWorkspaceSettings, MavenWorkspaceSettingsComponent}
import org.jetbrains.idea.maven.server.MavenServerManager
import java.{util => ju}

import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl
import io.snyk.plugin.ui.state.SnykPluginState
import org.apache.commons.io.FileUtils

abstract class AbstractMavenTestCase extends AbstractTestCase() {
  val POM_XML_FILE_NAME: String = "pom.xml"
  val SETTINGS_XML_FILE_NAME: String = "settings.xml"

  protected var myProjectsTree: MavenProjectsTree = _
  protected var myProjectsManager: MavenProjectsManager = _

  @throws[Exception]
  override protected def setUpInWriteAction(): Unit = {
    myProjectsManager = MavenProjectsManager.getInstance(currentProject)

    removeFromLocalMavenRepository("test")
  }

  @throws[Exception]
  override protected def setUp(): Unit = {
    super.setUp()

    MavenWorkspaceSettingsComponent.getInstance(currentProject).loadState(new MavenWorkspaceSettings)

    val home = getTestMavenHome

    if (home != null) {
      getMavenGeneralSettings.setMavenHome(home)
    }
  }

  @throws[Exception]
  override protected def tearDown(): Unit = {
    val projectDirectoryPath = currentProject.getBasePath

    new RunAll(
      () => SnykPluginState.removeForProject(currentProject),
      () => WriteAction.runAndWait(() => JavaAwareProjectJdkTableImpl.removeInternalJdkInTests()),
      () => MavenServerManager.getInstance.shutdown(true),
      () => MavenArtifactDownloader.awaitQuiescence(100, TimeUnit.SECONDS),
      () => EdtTestUtil.runInEdtAndWait(() => tearDownFixtures()),
      () => MavenIndicesManager.getInstance.clear(),
      () => super.tearDown(),
      () => resetClassFields(getClass),
      () => FileUtils.deleteDirectory(new File(projectDirectoryPath)),
      () => currentProject = null
    ).run()
  }

  private def getTestMavenHome = System.getProperty("idea.maven.test.home")

  protected def getMavenGeneralSettings = MavenProjectsManager.getInstance(currentProject).getGeneralSettings

  @throws[IOException]
  protected def restoreSettingsFile(): Unit = {
    updateSettingsXml()
  }

  @throws[IOException]
  protected def updateSettingsXml(content: String = "") = {
    val settingsXmlFile = new File(currentProject.getBasePath, SETTINGS_XML_FILE_NAME)
    settingsXmlFile.createNewFile

    val virtualFile = LocalFileSystem.getInstance.refreshAndFindFileByIoFile(settingsXmlFile)

    setFileContent(virtualFile, createSettingsXmlContent(content))

    getMavenGeneralSettings.setUserSettingsFile(virtualFile.getPath)

    virtualFile
  }

  private def createSettingsXmlContent(content: String) = {
    val mirror = System.getProperty("idea.maven.test.mirror", // use JB maven proxy server for internal use by default, see details at
      // https://confluence.jetbrains.com/display/JBINT/Maven+proxy+server
      "http://maven.labs.intellij.net/repo1")
    "<settings>" + content + "<mirrors>" + "  <mirror>" + "    <id>jb-central-proxy</id>" + "    <url>" + mirror + "</url>" + "    <mirrorOf>external:*,!flex-repository</mirrorOf>" + "  </mirror>" + "</mirrors>" + "</settings>"
  }



  protected def createProjectPom(xml: String): VirtualFile = {
    projectPomVirtualFile = createPomXmlVirtualFile(currentProject.getBaseDir, xml)

    projectPomVirtualFile
  }

  protected def createPomXmlVirtualFile(directoryVirtualFile: VirtualFile, xml: String): VirtualFile = {
    val pomXmlFile = new File(currentProject.getBasePath, POM_XML_FILE_NAME)
    pomXmlFile.createNewFile

    val pomXmlVirtualFile = LocalFileSystem.getInstance.refreshAndFindFileByIoFile(pomXmlFile)

    setFileContent(pomXmlVirtualFile, createPomXmlStr(xml))

    myAllPoms.add(pomXmlVirtualFile)

    pomXmlVirtualFile
  }

  @Language(value = "XML")
  def createPomXmlStr(@Language(value = "XML", prefix = "<project>", suffix = "</project>") xmlString: String) =
    "<?xml version=\"1.0\"?>" +
      "<project xmlns=\"http://maven.apache.org/POM/4.0.0\" " +
      "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"" +
      "         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\">" +
      "  <modelVersion>4.0.0</modelVersion>" +
      xmlString +
      "</project>"

  protected def importProject(@Language(value = "XML", prefix = "<project>", suffix = "</project>") xml: String): Unit = {
    createProjectPom(xml)
    importProject()
  }

  protected def importProject(): Unit = {
    importProjectWithProfiles()
  }

  protected def importProjectWithProfiles(profiles: String = ""): Unit = {
    doImportProjects(Collections.singletonList(projectPomVirtualFile), true, profiles)
  }

  protected def removeFromLocalMavenRepository(relativePath: String = ""): Unit = {
    if (SystemInfo.isWindows) {
      MavenServerManager.getInstance.shutdown(true)
    }

    FileUtil.delete(new File(getRepositoryPath, relativePath))
  }

  protected def getRepositoryPath = {
    val path = getRepositoryFile.getPath
    FileUtil.toSystemIndependentName(path)
  }

  protected def getRepositoryFile = getMavenGeneralSettings.getEffectiveLocalRepository

  protected def doImportProjects(files: ju.List[VirtualFile], failOnReadingError: Boolean, profiles: String): Unit = {
    initProjectsManager(true)

    readProjects(files, profiles)

    UIUtil.invokeAndWaitIfNeeded(new Runnable {
      override def run(): Unit = {
        myProjectsManager.waitForResolvingCompletion()
        myProjectsManager.scheduleImportInTests(files)
        myProjectsManager.importProjects
      }
    })

    if (failOnReadingError) {
      myProjectsTree.getProjects.forEach(mavenProject => {
        println("Failed to import Maven project: " + mavenProject.getProblems, mavenProject.hasReadingProblems)
      })
    }
  }

  protected def initProjectsManager(enableEventHandling: Boolean): Unit = {
    myProjectsManager.initForTests()
    myProjectsTree = myProjectsManager.getProjectsTreeForTests

    //    if (enableEventHandling) {
    //      myProjectsManager.enableAutoImportInTests()
    //    }
  }

  protected def readProjects(files: ju.List[VirtualFile], profiles: String): Unit = {
    myProjectsManager.resetManagedFilesAndProfilesInTests(files, new MavenExplicitProfiles(ju.Arrays.asList(profiles)))

    waitForReadingCompletion()
  }

  protected def waitForReadingCompletion(): Unit = {
    UIUtil.invokeAndWaitIfNeeded(new Runnable {
      override def run(): Unit = {
        try myProjectsManager.waitForReadingCompletion()
        catch {
          case e: Exception =>
            throw new RuntimeException(e)
        }
      }
    })
  }
}
