package io.snyk.plugin

import java.io.IOException
import java.lang.reflect.Modifier
import java.nio.charset.StandardCharsets
import java.util

import com.intellij.openapi.application.{ApplicationManager, ReadAction, WriteAction}
import com.intellij.openapi.project.Project

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.{EdtTestUtil, RunAll, UsefulTestCase}
import com.intellij.testFramework.fixtures.{IdeaProjectTestFixture, IdeaTestFixtureFactory}

import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl
import com.intellij.openapi.roots.ModuleRootModificationUtil

abstract class AbstractTestCase extends UsefulTestCase() {
  protected var myAllPoms: util.List[VirtualFile] = new util.ArrayList[VirtualFile]

  protected var myTestFixture: IdeaProjectTestFixture = _

  protected var currentProject: Project = _

  protected var projectPomVirtualFile: VirtualFile = _

  @throws[Exception]
  protected def setUpInWriteAction(): Unit = ???

  @throws[Exception]
  override protected def setUp(): Unit = {
    super.setUp()

    setUpFixtures()

    currentProject = myTestFixture.getProject

    EdtTestUtil.runInEdtAndWait(() => {
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
      () => currentProject = null,
      () => EdtTestUtil.runInEdtAndWait(() => tearDownFixtures()),
      () => super.tearDown(),
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

  @throws[Exception]
  protected def setUpFixtures(): Unit = {
    myTestFixture = IdeaTestFixtureFactory.getFixtureFactory
      .createFixtureBuilder(getName, false).getFixture

    myTestFixture.setUp()
  }

  protected def setFileContent(file: VirtualFile, content: String, advanceStamps: Boolean = true): Unit = {
    try WriteAction.runAndWait(() => {
      if (advanceStamps) {
        file.setBinaryContent(content.getBytes(StandardCharsets.UTF_8), -1, file.getTimeStamp + 4000)
      } else {
        file.setBinaryContent(content.getBytes(StandardCharsets.UTF_8), file.getModificationStamp, file.getTimeStamp)
      }
    })
    catch {
      case ioException: IOException => throw new RuntimeException(ioException)
    }
  }

  protected def setupJdkForModules(moduleNames: String*): Unit = {
    for (each <- moduleNames) {
      setupJdkForModule(each)
    }
  }

  protected def setupJdkForAllModules(): Unit = {
    ModuleManager.getInstance(currentProject).getModules.foreach(module => {
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
    ReadAction.compute(() => ModuleManager.getInstance(currentProject).findModuleByName(name))

  protected def waitBackgroundTasks(): Unit = waitBackgroundTasks(6)

  protected def wait10SecondsForBackgroundTasks(): Unit = waitBackgroundTasks(10)

  protected def waitBackgroundTasks(timeoutSeconds: Long): Unit = Thread.sleep(timeoutSeconds * 1000)
}
