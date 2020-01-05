package io.snyk.plugin

import java.io.{File, IOException}
import java.lang.reflect.Modifier
import java.nio.charset.StandardCharsets
import java.util
import java.util.Collections
import java.util.concurrent.TimeUnit

import com.intellij.openapi.application.{ApplicationManager, ReadAction, WriteAction}
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.{LocalFileSystem, VirtualFile}
import com.intellij.testFramework.{EdtTestUtil, RunAll, UsefulTestCase}
import com.intellij.testFramework.fixtures.{IdeaProjectTestFixture, IdeaTestFixtureFactory}
import com.intellij.util.ui.UIUtil
import org.intellij.lang.annotations.Language
import org.jetbrains.idea.maven.indices.MavenIndicesManager
import org.jetbrains.idea.maven.model.MavenExplicitProfiles
import org.jetbrains.idea.maven.project.{MavenArtifactDownloader, MavenProjectsManager, MavenProjectsTree,
                                         MavenWorkspaceSettings, MavenWorkspaceSettingsComponent}
import org.jetbrains.idea.maven.server.MavenServerManager
import java.{util => ju}

import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl
import com.intellij.openapi.roots.ModuleRootModificationUtil

abstract class AbstractMavenTestCase extends UsefulTestCase() {
  protected var myAllPoms: util.List[VirtualFile] = new util.ArrayList[VirtualFile]

  protected var myProjectsTree: MavenProjectsTree = _
  protected var myProjectsManager: MavenProjectsManager = _

  protected var myDir: File = _
  protected var myProjectRoot: VirtualFile = _
  private var ourTempDir: File = _

  protected var myTestFixture: IdeaProjectTestFixture = _

  protected var myProject: Project = _

  protected var myProjectPom: VirtualFile = _

  @throws[Exception]
  protected def setUpInWriteAction(): Unit = {
    val projectDir = new File(myDir, "project")

    projectDir.mkdirs

    myProjectRoot = LocalFileSystem.getInstance.refreshAndFindFileByIoFile(projectDir)
    myProjectsManager = MavenProjectsManager.getInstance(myProject)

    removeFromLocalMavenRepository("test")
  }

  @throws[Exception]
  override protected def setUp(): Unit = {
    super.setUp()

    checkProjectTempDirectoryCreated()

    myDir = new File(ourTempDir, getTestName(false))

    FileUtil.ensureExists(myDir)

    setUpFixtures()

    myProject = myTestFixture.getProject

    MavenWorkspaceSettingsComponent.getInstance(myProject).loadState(new MavenWorkspaceSettings)

    val home = getTestMavenHome

    if (home != null) {
      getMavenGeneralSettings.setMavenHome(home)
    }

    EdtTestUtil.runInEdtAndWait(() => {
      restoreSettingsFile()

      ApplicationManager.getApplication.runWriteAction(new Runnable {
        override def run(): Unit = {
          try setUpInWriteAction()
          catch {
            case throwable: Throwable =>
              throwable.printStackTrace()

              try tearDown()
              catch {
                case exception: Exception =>
                  exception.printStackTrace()
              }
              throw new RuntimeException(throwable)
          }
        }
      })
    })
  }

  @throws[Exception]
  protected def tearDownFixtures(): Unit = {
    //try myTestFixture.tearDown()
    //finally myTestFixture = null

    myTestFixture = null
  }

  @throws[Exception]
  override protected def tearDown(): Unit = {
    new RunAll(
      () => WriteAction.runAndWait(() => JavaAwareProjectJdkTableImpl.removeInternalJdkInTests()),
      () => MavenServerManager.getInstance.shutdown(true),
      () => MavenArtifactDownloader.awaitQuiescence(100, TimeUnit.SECONDS),
      () => myProject = null,
      () => EdtTestUtil.runInEdtAndWait(() => tearDownFixtures()),
      () => MavenIndicesManager.getInstance.clear(),
      () => super.tearDown(),
      () => {
        FileUtil.delete(myDir)
        // cannot use reliably the result of the com.intellij.openapi.util.io.FileUtil.delete() method
        // because com.intellij.openapi.util.io.FileUtilRt.deleteRecursivelyNIO() does not honor this contract
        if (myDir.exists) {
          System.err.println("Cannot delete " + myDir)
          //printDirectoryContent(myDir);
          myDir.deleteOnExit()
        }
      },
      () => resetClassFields(getClass)
    ).run()
  }

  private def resetClassFields(aClass: Class[_]): Unit = {
    if (aClass == null) {
      return
    }

    val fields = aClass.getDeclaredFields

    for (field <- fields) {
      val modifiers = field.getModifiers

      if ((modifiers & Modifier.FINAL) == 0 && (modifiers & Modifier.STATIC) == 0 && !field.getType.isPrimitive) {
        field.setAccessible(true)
        try field.set(this, null)
        catch {
          case e: IllegalAccessException =>
            e.printStackTrace()
        }
      }
    }

    if (aClass eq classOf[AbstractMavenTestCase]) {
      return
    }

    resetClassFields(aClass.getSuperclass)
  }

  @throws[IOException]
  private def checkProjectTempDirectoryCreated(): Unit = {
    if (ourTempDir != null) {
      return
    }

    ourTempDir = new File(FileUtil.getTempDirectory, "mavenTests")

    FileUtil.delete(ourTempDir)
    FileUtil.ensureExists(ourTempDir)
  }

  @throws[Exception]
  protected def setUpFixtures(): Unit = {
    myTestFixture = IdeaTestFixtureFactory.getFixtureFactory.createFixtureBuilder(getName).getFixture

    myTestFixture.setUp()
  }

  private def getTestMavenHome = System.getProperty("idea.maven.test.home")

  protected def getMavenGeneralSettings = MavenProjectsManager.getInstance(myProject).getGeneralSettings

  @throws[IOException]
  protected def restoreSettingsFile(): Unit = {
    updateSettingsXml("")
  }

  @throws[IOException]
  protected def updateSettingsXml(content: String) = updateSettingsXmlFully(createSettingsXmlContent(content))

  @throws[IOException]
  protected def updateSettingsXmlFully(@Language("XML") content: String) = {
    val ioFile = new File(myDir, "settings.xml")
    ioFile.createNewFile

    val virtualFile = LocalFileSystem.getInstance.refreshAndFindFileByIoFile(ioFile)

    setFileContent(virtualFile, content, true)

    getMavenGeneralSettings.setUserSettingsFile(virtualFile.getPath)

    virtualFile
  }

  private def createSettingsXmlContent(content: String) = {
    val mirror = System.getProperty("idea.maven.test.mirror", // use JB maven proxy server for internal use by default, see details at
      // https://confluence.jetbrains.com/display/JBINT/Maven+proxy+server
      "http://maven.labs.intellij.net/repo1")
    "<settings>" + content + "<mirrors>" + "  <mirror>" + "    <id>jb-central-proxy</id>" + "    <url>" + mirror + "</url>" + "    <mirrorOf>external:*,!flex-repository</mirrorOf>" + "  </mirror>" + "</mirrors>" + "</settings>"
  }

  private def setFileContent(file: VirtualFile, content: String, advanceStamps: Boolean): Unit = {
    try WriteAction.runAndWait(() => {
      if (advanceStamps) {
        file.setBinaryContent(content.getBytes(StandardCharsets.UTF_8), -1, file.getTimeStamp + 4000)
      } else {
        file.setBinaryContent(content.getBytes(StandardCharsets.UTF_8), file.getModificationStamp, file.getTimeStamp)
      }
    })
    catch {
      case e: IOException =>
        throw new RuntimeException(e)
    }
  }

  protected def createProjectPom(xml: String): VirtualFile = {
    myProjectPom = createPomFile(myProjectRoot, xml)

    myProjectPom
  }

  protected def createPomFile(directoryVirtualFile: VirtualFile, xml: String): VirtualFile = {
    var pomXmlVirtualFile = directoryVirtualFile.findChild("pom.xml")

    if (pomXmlVirtualFile == null) {
      try pomXmlVirtualFile = WriteAction.computeAndWait(() => {
        directoryVirtualFile.createChildData(null, "pom.xml")
      })
      catch {
        case exception: IOException =>
          throw new RuntimeException(exception)
      }

      myAllPoms.add(pomXmlVirtualFile)
    }

    setFileContent(pomXmlVirtualFile, createPomXml(xml), true)

    pomXmlVirtualFile
  }

  @Language(value = "XML")
  def createPomXml(@Language(value = "XML", prefix = "<project>", suffix = "</project>") xml: String) =
    "<?xml version=\"1.0\"?>" +
      "<project xmlns=\"http://maven.apache.org/POM/4.0.0\" " +
      "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"" +
      "         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\">" +
      "  <modelVersion>4.0.0</modelVersion>" +
      xml +
      "</project>"

  protected def importProject(@Language(value = "XML", prefix = "<project>", suffix = "</project>") xml: String): Unit = {
    createProjectPom(xml)
    importProject()
  }

  protected def importProject(): Unit = {
    importProjectWithProfiles("")
  }

  protected def importProjectWithProfiles(profiles: String): Unit = {
    doImportProjects(Collections.singletonList(myProjectPom), true, profiles)
  }

  protected def removeFromLocalMavenRepository(relativePath: String): Unit = {
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

  protected def setupJdkForModules(moduleNames: String*): Unit = {
    for (each <- moduleNames) {
      setupJdkForModule(each)
    }
  }

  protected def setupJdkForAllModules(): Unit = {
    ModuleManager.getInstance(myProject).getModules.foreach(module => {
      val sdk = JavaAwareProjectJdkTableImpl.getInstanceEx.getInternalJdk

      ModuleRootModificationUtil.setModuleSdk(module, sdk)
    })
  }

  protected def setupJdkForModule(moduleName: String) = {
    val sdk = JavaAwareProjectJdkTableImpl.getInstanceEx.getInternalJdk

    ModuleRootModificationUtil.setModuleSdk(getModule(moduleName), sdk)

    sdk
  }

  protected def getModule(name: String) =
    ReadAction.compute(() => ModuleManager.getInstance(myProject).findModuleByName(name))
}
