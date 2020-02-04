package io.snyk.plugin

import java.io.{File, FileOutputStream, IOException, StringWriter}
import java.net.URISyntaxException
import java.util
import java.util.jar.{Attributes, JarEntry, JarOutputStream, Manifest}
import java.util.zip.{ZipException, ZipFile}
import java.util.Properties

import com.intellij.compiler.server.BuildManager
import com.intellij.mock.MockApplicationEx
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.{PathManager, WriteAction}
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.externalSystem.importing.{ImportSpec, ImportSpecBuilder}
import com.intellij.openapi.externalSystem.model.{DataNode, ProjectSystemId}
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.model.settings.ExternalSystemExecutionSettings
import com.intellij.openapi.externalSystem.model.task.{ExternalSystemTaskId, ExternalSystemTaskNotificationListenerAdapter}
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemProgressNotificationManager
import com.intellij.openapi.externalSystem.service.project.{ExternalProjectRefreshCallback, ProjectDataManager}
import com.intellij.openapi.externalSystem.settings.AbstractExternalSystemSettings
import com.intellij.openapi.externalSystem.util.{ExternalSystemApiUtil, ExternalSystemUtil}
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil
import com.intellij.openapi.projectRoots.{JavaSdk, ProjectJdkTable, Sdk, SimpleJavaSdkType}
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.ui.{Messages, TestDialog}
import com.intellij.openapi.util.{Couple, Pair, Ref}
import com.intellij.openapi.util.io.{ByteArraySequence, FileUtil}
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.openapi.vfs.{JarFileSystem, LocalFileSystem, VirtualFile}
import com.intellij.testFramework.{IdeaTestUtil, RunAll}
import com.intellij.util.io.PathKt
import com.intellij.util.{ArrayUtilRt, PathUtil, SmartList}
import com.intellij.util.lang.JavaVersion
import io.snyk.plugin.ui.state.SnykPluginState
import org.apache.commons.io.FileUtils
import org.junit.Assert.fail
import org.gradle.StartParameter
import org.gradle.util.{DistributionLocator, GradleVersion}
import org.gradle.wrapper.{GradleWrapperMain, PathAssembler}
import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.gradle.settings.{DistributionType, GradleProjectSettings, GradleSettings, GradleSettingsListener}
import org.jetbrains.plugins.gradle.util.{GradleConstants, GradleUtil}
import org.junit.Assume
import org.junit.Assert.assertNotNull

abstract class AbstractGradleTestCase extends AbstractTestCase {

  protected val GRADLE_JDK_NAME = "Gradle JDK"

  protected val gradleVersion: String = "5.2.1"

  protected val GRADLE_DAEMON_TTL_MS = 10000

  protected var myProjectConfig: VirtualFile = _

  protected var myAllConfigs: util.List[VirtualFile] = new util.ArrayList[VirtualFile]

  protected var myProjectRoot: VirtualFile = _

  protected var myProjectSettings: GradleProjectSettings = _

  protected var myJdkHome: String = _

  protected var importProjectSpec: ImportSpec = _

  @throws[Exception]
  override protected def setUpInWriteAction(): Unit = {
    val projectDirectory = new File(currentProject.getBasePath)
    FileUtil.ensureExists(projectDirectory)

    myProjectRoot = LocalFileSystem.getInstance.refreshAndFindFileByIoFile(projectDirectory)
  }

  @throws[Exception]
  override protected def setUp(): Unit = {
    myJdkHome = requireRealJdkHome

    super.setUp()

    val allowedRoots = new util.ArrayList[String]

    collectAllowedRoots(allowedRoots)

    if (!allowedRoots.isEmpty) {
      VfsRootAccess.allowRootAccess(myTestFixture.getTestRootDisposable, ArrayUtilRt.toStringArray(allowedRoots): _*)
    }

    WriteAction.runAndWait(() => {
      val defaultJdk = JavaSdk.getInstance.createJdk("Default JDK", myJdkHome)
      ProjectJdkTable.getInstance.addJdk(defaultJdk)

      val oldJdk = ProjectJdkTable.getInstance.findJdk(GRADLE_JDK_NAME)

      if (oldJdk != null) {
        ProjectJdkTable.getInstance.removeJdk(oldJdk)
      }

      val jdkHomeDirVirtualFile = LocalFileSystem.getInstance.refreshAndFindFileByIoFile(new File(myJdkHome))
      val javaSdk = JavaSdk.getInstance

      val javaSdkType = if (javaSdk == null) {
        SimpleJavaSdkType.getInstance
      } else {
        javaSdk
      }

      val jdk = SdkConfigurationUtil
        .setupSdk(Array[Sdk](defaultJdk), jdkHomeDirVirtualFile, javaSdkType, true, null, GRADLE_JDK_NAME)

      assertNotNull("Cannot create JDK for " + myJdkHome, jdk)

      ProjectJdkTable.getInstance.addJdk(jdk)

      ProjectRootManager.getInstance(currentProject).setProjectSdk(jdk)
    })

    myProjectSettings = new GradleProjectSettings()
    myProjectSettings.setUseQualifiedModuleNames(true)

    System.setProperty(ExternalSystemExecutionSettings.REMOTE_PROCESS_IDLE_TTL_IN_MS_KEY, String.valueOf(GRADLE_DAEMON_TTL_MS))

    val distribution = WriteAction.computeAndWait(() => configureWrapper)

    val newAllowedRoots = new util.ArrayList[String]
    collectAllowedRoots(newAllowedRoots, distribution)

    if (!newAllowedRoots.isEmpty) {
      VfsRootAccess.allowRootAccess(myTestFixture.getTestRootDisposable, ArrayUtilRt.toStringArray(newAllowedRoots): _*)
    }
  }

  protected def collectAllowedRoots(roots: util.List[String]): Unit = {
    roots.add(myJdkHome)
    roots.addAll(collectRootsInside(myJdkHome))
    roots.add(PathManager.getConfigPath)
  }

  protected def collectAllowedRoots(roots: util.List[String], distribution: PathAssembler#LocalDistribution): Unit = {
    roots.add(distribution.getDistributionDir.getAbsolutePath)
  }

  def collectRootsInside(root: String) = {
    val roots = new SmartList[String]

    roots.add(root)

    FileUtil.processFilesRecursively(new File(root), (file: File) => {
      try {
        val path = file.getCanonicalPath

        if (!FileUtil.isAncestor(path, path, false)) {
          roots.add(path)
        }
      } catch {
        case ignore: IOException =>
      }
      true
    })

    roots
  }

  @throws[IOException]
  protected def importProject(@Language("Groovy") config: String): Unit = {
    val fullConfig = injectRepo(config)

    createProjectConfig(fullConfig)

    doImportProject()
  }

  protected def getCurrentExternalProjectSettings = myProjectSettings

  private def doImportProject(): Unit = {
    val systemSettings =
      ExternalSystemApiUtil.getSettings(currentProject, GradleConstants.SYSTEM_ID)
        .asInstanceOf[AbstractExternalSystemSettings[GradleSettings, GradleProjectSettings, GradleSettingsListener]]

    val projectSettings = getCurrentExternalProjectSettings

    projectSettings.setExternalProjectPath(getProjectPath)

    val projects = new util.HashSet[GradleProjectSettings](systemSettings.getLinkedProjectsSettings)

    projects.remove(projectSettings) // FIXME: Is this needed?
    projects.add(projectSettings)

    systemSettings.setLinkedProjectsSettings(projects)

    val error: Ref[Couple[String]] = Ref.create()
    var importSpecBuilder = createImportSpec
    val callback = importSpecBuilder.getCallback

    if (callback == null) {
      importSpecBuilder = new ImportSpecBuilder(importSpecBuilder).callback(new ExternalProjectRefreshCallback() {
        override def onSuccess(externalProject: DataNode[ProjectData]): Unit = {
          if (externalProject == null) {
            println("Got null External project after import")

            return
          }

          ServiceManager.getService(classOf[ProjectDataManager]).importData(externalProject, currentProject, true)

          println("External project was successfully imported")

          importProjectSpec = importSpecBuilder
        }

        override

        def onFailure(errorMessage: String, errorDetails: String): Unit = {
          error.set(Couple.of(errorMessage, errorDetails))
        }
      }).build
    }

    val notificationManager = ServiceManager.getService(classOf[ExternalSystemProgressNotificationManager])

    val listener = new ExternalSystemTaskNotificationListenerAdapter() {
      override def onTaskOutput(id: ExternalSystemTaskId, text: String, stdOut: Boolean): Unit = {
        if (StringUtil.isEmptyOrSpaces(text)) {
          return
        }

        (if (stdOut) System.out else System.err).print(text)
      }
    }

    notificationManager.addNotificationListener(listener)

    try ExternalSystemUtil.refreshProjects(importSpecBuilder)
    finally notificationManager.removeNotificationListener(listener)

    if (!error.isNull) {
      handleImportFailure(error.get.first, error.get.second)
    }
  }

  protected def handleImportFailure(errorMessage: String, errorDetails: String): Unit = {
    var failureMsg = "Import failed: " + errorMessage

    if (StringUtil.isNotEmpty(errorDetails)) {
      failureMsg += "\nError details: \n" + errorDetails
    }

    fail(failureMsg)
  }

  protected def createImportSpec = {
    val importSpecBuilder = new ImportSpecBuilder(currentProject, getExternalSystemId)
      .use(ProgressExecutionMode.MODAL_SYNC)
      .forceWhenUptodate

    importSpecBuilder.withArguments("--continue")

    importSpecBuilder.build
  }

  private def getExternalSystemId: ProjectSystemId = GradleConstants.SYSTEM_ID

  protected def injectRepo(@Language("Groovy") config: String) = {
    "allprojects {\n" + "  repositories {\n" + "    maven {\n" + " url 'http://maven.labs.intellij.net/repo1'\n" + " }\n" + "  }" + "}\n" + config
  }

  protected def createProjectConfig(config: String) = {
    myProjectConfig = createConfigFile(myProjectRoot, config)

    myProjectConfig
  }

  protected def createConfigFile(dir: VirtualFile, config: String) = {
    val configFileName = getExternalSystemConfigFileName

    try {
      val configFile = WriteAction.computeAndWait(() => {
        val file: VirtualFile = dir.findChild(configFileName)

        if (file == null) {
          dir.createChildData(null, configFileName)
        } else {
          file
        }
      })

      myAllConfigs.add(configFile)

      setFileContent(configFile, config, true)

      configFile
    } catch {
      case e: IOException =>
        throw new RuntimeException(e)
    }
  }

  protected def getProjectPath = myProjectRoot.getPath

  protected def getExternalSystemConfigFileName = "build.gradle"

  private def requireRealJdkHome: String = {
    val javaRuntimeVersion = JavaVersion.current

    assumeTestJavaRuntime(javaRuntimeVersion)

    IdeaTestUtil.requireRealJdkHome
  }

  protected def assumeTestJavaRuntime(javaRuntimeVersion: JavaVersion): Unit = {
    val javaVersion = javaRuntimeVersion.feature
    val gradleBaseVersion = getCurrentGradleBaseVersion

    Assume.assumeFalse("Skip integration tests running on JDK "
      + javaVersion + "(>9) for "
      + gradleBaseVersion + "(<3.0)", javaVersion > 9 && gradleBaseVersion.compareTo(GradleVersion.version("3.0")) < 0)
  }

  protected def getCurrentGradleBaseVersion = GradleVersion.version(gradleVersion).getBaseVersion

  private def wrapperJar = new File(PathUtil.getJarPathForClass(classOf[GradleWrapperMain]))

  @throws[IOException]
  protected def createProjectSubFile(relativePath: String): VirtualFile = {
    val file = new File(getProjectPath, relativePath)

    FileUtil.ensureExists(file.getParentFile)
    FileUtil.ensureCanCreateFile(file)

    val created = file.createNewFile

    if (!created && !file.exists) {
      throw new AssertionError("Unable to create the project sub file: " + file.getAbsolutePath)
    }

    LocalFileSystem.getInstance.refreshAndFindFileByIoFile(file)
  }

  @throws[IOException]
  @throws[URISyntaxException]
  private def configureWrapper = {
    val distributionUri = new DistributionLocator().getDistributionFor(GradleVersion.version(gradleVersion))

    myProjectSettings.setDistributionType(DistributionType.DEFAULT_WRAPPED)

    val wrapperJarFrom = LocalFileSystem.getInstance.refreshAndFindFileByIoFile(wrapperJar)

    assert(wrapperJarFrom != null)

    val wrapperJarFromTo = createProjectSubFile("gradle/wrapper/gradle-wrapper.jar")

    WriteAction.runAndWait(() => wrapperJarFromTo.setBinaryContent(wrapperJarFrom.contentsToByteArray))

    val properties = new Properties
    properties.setProperty("distributionBase", "GRADLE_USER_HOME")
    properties.setProperty("distributionPath", "wrapper/dists")
    properties.setProperty("zipStoreBase", "GRADLE_USER_HOME")
    properties.setProperty("zipStorePath", "wrapper/dists")
    properties.setProperty("distributionUrl", distributionUri.toString)

    val writer = new StringWriter

    properties.store(writer, null)
    createProjectSubFile("gradle/wrapper/gradle-wrapper.properties", writer.toString)

    val wrapperConfiguration = GradleUtil.getWrapperConfiguration(getProjectPath)
    val localDistribution = new PathAssembler(StartParameter.DEFAULT_GRADLE_USER_HOME).getDistribution(wrapperConfiguration)
    val zip = localDistribution.getZipFile

    try if (zip.exists) {
      val zipFile = new ZipFile(zip)

      zipFile.close()
    }
    catch {
      case e: ZipException =>
        e.printStackTrace()

        System.out.println("Corrupted file will be removed: " + zip.getPath)

        FileUtil.delete(zip)
      case e: IOException =>
        e.printStackTrace()
    }

    localDistribution
  }

  @throws[IOException]
  protected def createProjectSubFile(relativePath: String, content: String): VirtualFile = {
    val file = createProjectSubFile(relativePath)

    setFileContent(file, content, false)

    file
  }

  @throws[IOException]
  protected def createProjectJarSubFile(relativePath: String, contentEntries: Pair[ByteArraySequence, String]*) = {
    val file = new File(getProjectPath, relativePath)

    FileUtil.ensureExists(file.getParentFile)
    FileUtil.ensureCanCreateFile(file)

    val isFileCreated = file.createNewFile

    if (!isFileCreated) {
      throw new AssertionError("Unable to create the project sub file: " + file.getAbsolutePath)
    }

    val manifest = new Manifest

    manifest.getMainAttributes.put(Attributes.Name.MANIFEST_VERSION, "1.0")

    val targetJarOutputStream = new JarOutputStream(new FileOutputStream(file), manifest)

    for (contentEntry <- contentEntries) {
      addJarEntry(contentEntry.first.getBytes, contentEntry.second, targetJarOutputStream)
    }

    targetJarOutputStream.close()

    val virtualFile = LocalFileSystem.getInstance.refreshAndFindFileByIoFile(file)

    assertNotNull(virtualFile)

    val jarFile = JarFileSystem.getInstance.getJarRootForLocalFile(virtualFile)

    assertNotNull(jarFile)

    jarFile
  }

  @throws[IOException]
  private def addJarEntry(bytes: Array[Byte], path: String, target: JarOutputStream): Unit = {
    val jarEntry = new JarEntry(path.replace("\\", "/"))

    target.putNextEntry(jarEntry)
    target.write(bytes)
    target.close()
  }

  @throws[Exception]
  override def tearDown(): Unit = {
    if (myJdkHome == null) {
      return
    }

    val projectDirectoryPath = currentProject.getBasePath

    new RunAll(
      () => SnykPluginState.removeForProject(currentProject),
      () => {
        val jdk = ProjectJdkTable.getInstance.findJdk(GRADLE_JDK_NAME)

        if (jdk != null) {
          WriteAction.runAndWait(() => {
            ProjectJdkTable.getInstance.removeJdk(jdk)

            if (currentProject != null) {
              ProjectRootManager.getInstance(currentProject).setProjectSdk(null)
            }
          })
        }
      },
      () => {
        Messages.setTestDialog(TestDialog.DEFAULT)

        deleteBuildSystemDirectory()
      },
      () => super.tearDown(),
      () => FileUtils.deleteDirectory(new File(projectDirectoryPath)),
      () => currentProject = null)
      .run()
  }

  def deleteBuildSystemDirectory(): Unit = {
    val buildManager = BuildManager.getInstance

    if (buildManager == null) {
      return
    }

    val buildSystemDirectory = buildManager.getBuildSystemDirectory

    try {
      PathKt.delete(buildSystemDirectory)
      return
    } catch {
      case ignore: Exception =>
    }

    try FileUtil.delete(buildSystemDirectory.toFile)
    catch {
      case e: Exception => println("Unable to remove build system directory.", e)
    }
  }

  @throws[IOException]
  protected def createSettingsFile(@Language("Groovy") content: String) =
    createProjectSubFile("settings.gradle", content)

  class GradleMockApplication(parentDisposable: Disposable) extends MockApplicationEx(parentDisposable) {
    override def isUnitTestMode: Boolean = false
  }
}
