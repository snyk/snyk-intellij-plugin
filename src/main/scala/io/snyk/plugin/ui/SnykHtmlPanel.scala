package io.snyk.plugin.ui

import com.intellij.openapi.project.Project
import com.sun.javafx.application.PlatformImpl
import io.snyk.plugin.embeddedserver.MiniServer
import io.snyk.plugin.model.SnykPluginState
import javafx.embed.swing.JFXPanel
import javafx.scene.Scene
import javafx.scene.paint.Color
import javafx.scene.web.WebView
import javafx.concurrent.Worker
import monix.execution.atomic.Atomic

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future, Promise}

class SnykHtmlPanel(project: Project, pluginState: Atomic[SnykPluginState]) extends JFXPanel { self =>
  val ms = new MiniServer(project, pluginState)
  val browser: Future[WebView] = initBrowser()

  pluginState.transform(_.withHtmlPanel(this))

//  browser.foreach(loadContent(_, project))
  navigateTo("/html/start.hbs").foreach(url =>
    println(s"done loading start page from $url")
  )

  def navigateTo(url: String): Future[String] = {
    val absoluteUrl = ms.rootUrl.toURI.resolve(url).toString
    println(s"navigating to $url [$absoluteUrl]")
    browser flatMap { b =>
      val p = Promise[String]
      PlatformImpl.runLater { () =>
        val engine = b.getEngine
        val loadWorker = engine.getLoadWorker
        val stateProperty = loadWorker.stateProperty

        stateProperty.addListener(CallbackChangeListener[Worker.State]{(oldValue, newValue, followup) =>
          if(newValue == Worker.State.SUCCEEDED) {
            followup.removeListener = true
            p.success(absoluteUrl)
          }
        })
        b.getEngine.load(absoluteUrl)
      }
      p.future
    }
  }

  private def initBrowser(): Future[WebView] = {
    val ret = Promise[WebView]()

    PlatformImpl.setImplicitExit(false)
    PlatformImpl.runLater {
      () => {
        val browser = new WebView()
        val scene = new Scene(browser, Color.ALICEBLUE)
        self.setScene(scene)
        ret.success(browser)
      }
    }

    ret.future
  }

//  private def loadContent(browser: WebView, project: Project) {
//    println("registered handlers:" + System.getProperty("java.protocol.handler.pkgs"))
//
//    navigateTo("/html/start.hbs")
//
////    PlatformImpl.runLater {
////      () => {
////        val webEngine = browser.getEngine
////        // libTable fallback for when we can't be smarter about maven/grade/sbt/etc.
////        //            val libTable = LibraryTablesRegistrar.getInstance().getLibraryTable(project)
////        //            libTable.libraries.forEach {
////        //                lib -> buf.append("<li>${lib.name}</li>")
////        //            }
////
//////        val builder = new StringBuilder("")
//////        builder.append(s"<a href='http://localhost:${ms.port}/html/deptree.hbs'>Dependency Tree</a></br>")
//////        builder.append("</table>")
//////        webEngine.loadContent(builder.toString())
////        navigateTo("/html/start.hbs")
////      }
////    }
//  }

}
