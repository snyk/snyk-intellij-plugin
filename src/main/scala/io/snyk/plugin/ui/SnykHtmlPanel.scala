package io.snyk.plugin.ui

import com.intellij.openapi.project.Project
import com.sun.javafx.application.PlatformImpl
import io.snyk.plugin.embeddedserver.{ColorProvider, MiniServer, ParamSet}
import io.snyk.plugin.ui.state.SnykPluginState
import javafx.embed.swing.JFXPanel
import javafx.scene.Scene
import javafx.scene.paint.Color
import javafx.scene.web.WebView
import javax.swing.UIManager

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future, Promise}
import javafx.concurrent.Worker

/**
  * Provides the HTML view that's central to this plugin, and initialises the underlying
  * embedded HTTP server that will feed it.
  */
class SnykHtmlPanel(project: Project, pluginState: SnykPluginState) extends JFXPanel { self =>
  val ms = new MiniServer(pluginState, ColorProvider.intellij)

  val browser: Future[WebView] = initBrowser()
  val externalPopupHandler = new ExternalPopupHandler(pluginState)

  navigateTo("/html/select-project.hbs", ParamSet.Empty.plus("redirectUrl" -> "/html/vulns.hbs")).foreach(url =>
    println(s"done loading start page from $url")
  )

  /**
    * Navigate to the specified path.  In normal use this will *not* be called directly.
    * Instead, `pluginState.navigateTo` will be called, allowing for interception of "special" URLs
    * - such as videos - which can then be shown via an alternate mechanism.
    * @param path
    * @param params
    * @return
    */
  private[ui] def navigateTo(path: String, params: ParamSet): Future[String] = {
    val url = ms.rootUrl.toURI.resolve(path).toString + params.queryString
    println(s"navigating to $path [$url]")
    browser flatMap { b =>
      val p = Promise[String]
      PlatformImpl.runLater { () =>
        import io.snyk.plugin.Implicits.RichJfxWorker
        b.getEngine.getLoadWorker onNextSucceeded { p.success(url) }
        b.getEngine.load(url)
      }
      p.future
    }
  }

  def reload(): Future[String] = browser flatMap { b =>
    val p = Promise[String]
    PlatformImpl.runLater { () =>
      b.getEngine.reload()
      p.success(b.getEngine.getLocation)
    }
    p.future
  }

  //var dwss: Option[DebugWebSocketServer] = None

  private def initBrowser(): Future[WebView] = {
    val ret = Promise[WebView]()

    PlatformImpl.setImplicitExit(false)
    PlatformImpl.runLater {
      () => {
        import com.sun.javafx.webkit.WebConsoleListener
        WebConsoleListener setDefaultListener { (webView, message, lineNumber, sourceId) =>
            println(s"$message [at $lineNumber]")
        }
        val browser = new WebView()
        val engine = browser.getEngine

        engine.setUserAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_12_6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/61.0.3163.100 Safari/537.36")
//        dwss = Some(new DebugWebSocketServer(engine))

        engine.getLoadWorker.stateProperty addListener { (obs, oldVal, newVal) =>
          if (newVal == Worker.State.SUCCEEDED) {
            println("Page loaded")
          }
        }
        engine.setCreatePopupHandler(externalPopupHandler)
        UIManager.addPropertyChangeListener { evt =>
          PlatformImpl.runLater { () => engine.reload() }
        }
        val scene = new Scene(browser, Color.ALICEBLUE)
        self.setScene(scene)
        ret.success(browser)
      }
    }

    ret.future
  }
}
