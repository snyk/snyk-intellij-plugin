import io.snyk.plugin.embeddedserver.{ColorProvider, HandlebarsEngine, MiniServer}
import io.snyk.plugin.model._
import scala.concurrent.ExecutionContext.Implicits.global

object PreviewHtmlUi extends App {
  val handlebars = new HandlebarsEngine

  val pluginState = SnykPluginState.mock
  val colorProvider = ColorProvider.mockDarkula

  val miniServer = new MiniServer(pluginState, colorProvider, 7695)

  pluginState.selectedProjectId := "dummy root"
  pluginState.performScan().onComplete { case _ =>
    val root = miniServer.rootUrl.toURI
    val testUrl = root.resolve("/html/vulns.hbs")
    println(s"opening browser to $testUrl")
    java.awt.Desktop.getDesktop.browse(testUrl)
  }


}
