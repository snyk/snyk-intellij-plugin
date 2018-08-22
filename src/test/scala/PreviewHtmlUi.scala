import io.snyk.plugin.datamodel.SnykMavenArtifact
import io.snyk.plugin.embeddedserver.{ColorProvider, HandlebarsEngine, MiniServer}
import io.snyk.plugin.ui.state._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.io.{Codec, Source}
import scala.util.Try


object PreviewHtmlUi extends App {
  org.apache.log4j.BasicConfigurator.configure()
  Log4jLoggerFactory.install()

  private[this] def myMockResponder(treeRoot: SnykMavenArtifact): Try[String] = Try {
    Source.fromResource("sample-response-2.json", getClass.getClassLoader)(Codec.UTF8).mkString
  }

  val handlebars = new HandlebarsEngine

  val pluginState = SnykPluginState.mock(mockResponder = myMockResponder)
  val colorProvider = ColorProvider.mockIntellijDarkula

  val miniServer = new MiniServer(pluginState, colorProvider, 7695)

  pluginState.selectedProjectId := "dummy root"
  pluginState.performScan().onComplete { _ =>
    val root = miniServer.rootUrl.toURI
    val testUrl = root.resolve("/vulnerabilities")
    println(s"opening browser to $testUrl")
    java.awt.Desktop.getDesktop.browse(testUrl)
  }


}
