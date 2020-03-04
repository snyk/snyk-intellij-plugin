package io.snyk.plugin
package ui

import java.net.URL

import com.intellij.ide.BrowserUtil
import io.snyk.plugin.ui.state.SnykPluginState
import javafx.application.Platform
import javafx.scene.web.PopupFeatures
import javafx.scene.web.WebEngine
import javafx.util.Callback

import scala.util.control.NonFatal

/**
  * The *only* way to handle a popup (e.g. target="_blank") in the JavaFX webview is via a callback that
  * provides the WebEngine that should handle the request.  The design is clearly fixated on keeping you
  * trapped within JavaFX at all costs, and therefore requires this pointlessly heavyweight hack to work around.
  *
  * On the off-chance that children might happen to read this comment, I shall refrain from giving my full and
  * frank opinion as to the quality of Oracle's engineering here and how usable the JavaFX WebView API is.
  * Suffice to say... I don't much like it.
  *
  * First, we need a WebEngine to use as a target.  It's a final class and can't be safely mocked, so we
  * have to provide a genuine instance, along with the underlying WebKit instance.
  * (did I mention this was heavyweight?)
  *
  * With this, we add a listener to capture the location that's pushed into it.  We then immediately stop it
  * from doing anything (did I mention this was POINTLESSLY heavyweight?) and pass the URL to our own `processUrl`
  * handler which will either:
  *  - process special http://idenav links to drive an internal IDE navigation
  *    (e.g. link to the source of a declaration of some dependency in a POM)
  *  - Use IntelliJ's `BrowserUtil.browse` to open the link in the user's browser
  */
class ExternalPopupHandler(pluginState: SnykPluginState)
extends Callback[PopupFeatures, WebEngine] with IntellijLogging {

  private[this] val navPrefix = "http://idenav/artifact/"

  lazy val zombieEngine: WebEngine = {
    val engine = new WebEngine
    engine.locationProperty addListener { (_, _, url) =>
      // stop loading and unload the url
      // -> does this internally: engine.getLoadWorker().cancelAndReset();
      // We can't call that method directly though, as it's protected - making this a hack within a hack
      Platform.runLater( () => engine.loadContent("") )
      if (url.nonEmpty) processUrl(url)
    }
    engine
  }

  private[this] def processUrl(url: String) = {
    log.info(s"External Popup Handler tackling: $url")

    try {
      if (url.startsWith(navPrefix)) {
        val parts = url.drop(navPrefix.length).split(":|@")

        val group = parts(0)
        val artifact = parts(1)
        val cliProjectName = parts(3)
        val cliTargetFile = parts(parts.size - 1)

        pluginState.navigator()
          .navigateToDependency(group, artifact, pluginState.selectedProjectId.get, cliProjectName, cliTargetFile)
      } else {
        BrowserUtil.browse(new URL(url))
      }
    } catch {
      case NonFatal(e) => log.warn(e)
    }
  }

  def call(popupFeatures: PopupFeatures): WebEngine = zombieEngine
}


