package io.snyk.plugin

import io.snyk.plugin.datamodel.SnykMavenArtifact
import io.snyk.plugin.embeddedserver.{ColorProvider, HandlebarsEngine, MiniServer}
import io.snyk.plugin.ui.state.{PerProjectState, SnykPluginState}
import org.junit.Test
import org.junit.Assert._

import scala.io.{Codec, Source}
import scala.util.Try
import scala.concurrent.ExecutionContext.Implicits.global

class MiniServerTest {
  org.apache.log4j.BasicConfigurator.configure()
  Log4jLoggerFactory.install()

  @Test
  def testStartMiniServer(): Unit = {
    val handlebars = new HandlebarsEngine

    val pluginState = SnykPluginState.mock(mockResponder = myMockResponder)
    val colorProvider = ColorProvider.mockIntellijDarkula

    val miniServer = new MiniServer(pluginState, colorProvider, 7695)

    pluginState.selectedProjectId := "dummy root"
    pluginState.projects := Map("dummy root" -> PerProjectState())

    pluginState.performScan().onComplete { _ =>
      val root = miniServer.rootUrl.toURI
      val vulnerabilitiesUrl = root.resolve("/vulnerabilities")

      assertEquals("http://localhost:7695/vulnerabilities", vulnerabilitiesUrl.toString)

      val requestResult = Source.fromURL(vulnerabilitiesUrl.toString).mkString

      assertTrue(requestResult.contains("<svg id=\"anilogo\" class=\"anilogo\"></svg>"))
    }

    Thread.sleep(5000)
  }

  private[this] def myMockResponder(treeRoot: SnykMavenArtifact): Try[String] = Try {
    Source.fromResource("sample-response-2.json", getClass.getClassLoader)(Codec.UTF8).mkString
  }
}
