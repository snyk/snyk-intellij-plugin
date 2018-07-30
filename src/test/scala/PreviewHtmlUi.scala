import io.snyk.plugin.embeddedserver.{ColorProvider, HandlebarsEngine, MiniServer}
import io.snyk.plugin.model._
import io.snyk.plugin.client.ApiClient

object PreviewHtmlUi extends App {
  val handlebars = new HandlebarsEngine

  val pluginState = SnykPluginState.mock
  val depTreeProvider = DepTreeProvider.mock
  val colorProvider = ColorProvider.mockDarkula
  val apiClient = ApiClient.mock()

  val initDepTree = depTreeProvider.getDepTree()
  pluginState.setDepTree(initDepTree.toDisplayNode)
  pluginState.latestScanResult set apiClient.runOn(initDepTree).get

  val miniServer = new MiniServer(pluginState, depTreeProvider, colorProvider, apiClient, 7695)

  val root = miniServer.rootUrl.toURI
  val testUrl = root.resolve("/html/vulns.hbs?requires=miniVulns")
  java.awt.Desktop.getDesktop.browse(testUrl)
}
