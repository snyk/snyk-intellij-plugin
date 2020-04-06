package io.snyk.plugin

import io.snyk.plugin.datamodel.ProjectDependency
import io.snyk.plugin.ui.state.SnykPluginState
import org.junit.Assert.{assertEquals, assertNotNull, assertTrue, fail}
import org.junit.Test

import monix.execution.Scheduler.Implicits.global
import io.snyk.plugin.depsource.MavenProjectsObservable
import monix.execution.Ack.Continue
import monix.reactive.Observable
import org.jetbrains.idea.maven.project.{MavenProject, MavenProjectsManager}

import scala.io.{Codec, Source}
import scala.util.Try

class MavenProjectsObservableTest extends AbstractMavenTestCase() {

  override protected def setUp(): Unit = {
    super.setUp()

    val projectXmlStr = Source.fromResource("sample-pom.xml", getClass.getClassLoader)(Codec.UTF8).mkString

    importProject(projectXmlStr)
  }

  @Test
  def testForProject(): Unit = {
    val observableSeq: Observable[Seq[MavenProject]] = MavenProjectsObservable.forProject(currentProject)

    assertNotNull(observableSeq)
  }

  @Test
  def testNoImportAndResolveScheduledEvent(): Unit = {
    SnykPluginState.mockForProject(currentProject, mockResponder = myMockResponder)

    val observable = MavenProjectsObservable.forProject(currentProject).map(_.map(_.toString))

    observable subscribe { list =>
      fail("The event importAndResolveScheduled() did not happen and should not get here.")

      Continue
    }

    waitBackgroundTasks()
  }

  @Test
  def testImportAndResolveScheduled(): Unit = {
    SnykPluginState.mockForProject(currentProject, mockResponder = myMockResponder)

    val observable = MavenProjectsObservable.forProject(currentProject).map(_.map(_.toString))

    var isEventExecuted = false

    observable subscribe { list =>
      assertNotNull(list)

      assertEquals("sampleProjectGroupdId:sampleArtifactId:1.0-SNAPSHOT", list.head)

      isEventExecuted = true

      Continue
    }

    scheduleImportAndResolveEvent()

    waitBackgroundTasks()

    assertTrue(isEventExecuted)
  }

  private[this] def myMockResponder(treeRoot: ProjectDependency): Try[String] = Try {
    Source.fromResource("sample-response-for-maven-observable-test.json", getClass.getClassLoader)(Codec.UTF8).mkString
  }

  private[this] def scheduleImportAndResolveEvent() = {
    val mavenProjectsManager = MavenProjectsManager.getInstance(currentProject)

    mavenProjectsManager.scheduleImportAndResolve()
  }
}
