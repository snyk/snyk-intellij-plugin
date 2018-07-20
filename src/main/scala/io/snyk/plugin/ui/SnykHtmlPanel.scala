package io.snyk.plugin.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.sun.javafx.application.PlatformImpl
import io.snyk.plugin.embeddedserver.MiniServer
import javafx.embed.swing.JFXPanel
import javafx.scene.Scene
import javafx.scene.paint.Color
import javafx.scene.web.WebView
import org.jetbrains.idea.maven.model.MavenArtifact

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future, Promise}

class SnykHtmlPanel(project: Project) extends JFXPanel { self =>
  val ms = new MiniServer(project)
  val browser: Future[WebView] = initBrowser()

  browser.foreach(loadContent(_, project))

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

  private def loadContent(browser: WebView, project: Project) {
    println("registered handlers:" + System.getProperty("java.protocol.handler.pkgs"))

    PlatformImpl.runLater {
      () => {
        val webEngine = browser.getEngine

        val builder = new StringBuilder("")
        builder.append(s"<a href='http://localhost:${ms.port}/html/sample.hbs'>Dependency Tree</a></br>")
//        builder.append(s"<a href='http://localhost:${ms.port}/html/deps-test.html'>deps-test</a></br>")
        builder.append("<a href='http://snyk.io'>Snyk</a> plugin, these are your libraries:")
        builder.append("<table>")

        // libTable fallback for when we can't be smarter about maven/grade/sbt/etc.
        //            val libTable = LibraryTablesRegistrar.getInstance().getLibraryTable(project)
        //            libTable.libraries.forEach {
        //                lib -> buf.append("<li>${lib.name}</li>")
        //            }

        builder.append("<tr>")
        builder.append("<th>Group</th>")
        builder.append("<th>ID</th>")
        builder.append("<th>Type</th>")
        builder.append("<th>Classifier</th>")
        builder.append("<th>Version</th>")
        builder.append("<th>Scope</th>")
        builder.append("</tr>")

        builder.append("</table>")
        webEngine.loadContent(builder.toString())
      }
    }
  }

  private def appendArtefactAsTableRow(builder: StringBuilder, a: MavenArtifact) {
    builder.append(s"<tr>")
    builder.append(s"<td>${a.getGroupId}</td>")
    builder.append(s"<td>${a.getArtifactId}</td>")
    builder.append(s"<td>${a.getType}</td>")
    builder.append(s"<td>${a.getClassifier}</td>")
    val version = if(StringUtil.isEmptyOrSpaces(a.getBaseVersion)) a.getVersion else a.getBaseVersion
    builder.append(s"<td>$version</td>")
    builder.append(s"<td>${a.getScope}</td>")
    builder.append(s"</tr>")
  }
}
