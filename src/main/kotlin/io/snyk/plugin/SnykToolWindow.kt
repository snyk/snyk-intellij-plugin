package io.snyk.plugin

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import javafx.scene.web.WebView
import javafx.embed.swing.JFXPanel
import javafx.scene.Scene
import com.sun.javafx.application.PlatformImpl
import io.snyk.plugin.urlproto.snykplugin.HandlerFactory
import javafx.scene.paint.Color
import org.jetbrains.idea.maven.model.MavenArtifact
import java.net.URL
import java.util.concurrent.CompletableFuture

class SnykToolWindow : ToolWindowFactory {

    private var jfxPanel: JFXPanel? = null
    private var browser: WebView? = null

    private fun initBrowser(panel: JFXPanel): CompletableFuture<WebView> {
        val ret = CompletableFuture<WebView>()

        PlatformImpl.setImplicitExit(false)
        PlatformImpl.runLater{
            val browser = WebView()
            val scene = Scene(browser, Color.ALICEBLUE)
            panel.scene = scene
            ret.complete(browser)
        }
        return ret
    }


    private fun appendArtefactAsTableRow(builder: StringBuilder, a: MavenArtifact) {
        builder.append("<tr>")
        builder.append("<td>${a.groupId}</td>")
        builder.append("<td>${a.artifactId}</td>")
        builder.append("<td>${a.type}</td>")
        builder.append("<td>${a.classifier}</td>")
        val version = if(StringUtil.isEmptyOrSpaces(a.baseVersion)) a.version else a.baseVersion
        builder.append("<td>$version</td>")
        builder.append("<td>${a.scope}</td>")
        builder.append("</tr>")
    }

    private fun loadContent(browser: WebView, project: Project) {
        URL.setURLStreamHandlerFactory(HandlerFactory(project))

        println("registered handlers:" + System.getProperty("java.protocol.handler.pkgs"))

        PlatformImpl.runLater {
            val webEngine = browser.engine

            val builder = StringBuilder("")
            builder.append("<a href='snykplugin://_/html/sample.html'>internal test link</a></br>")
            builder.append("<a href='snykplugin://_/html/sample.html.templ'>template test link</a></br>")
            builder.append("<a href='snykplugin://_/html/deps-test.html'>deps-test</a></br>")
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

            val depRoot = project.dependencyTreeRoot()
            builder.append("</table>")
            webEngine.loadContent(builder.toString())
        }
    }

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        jfxPanel = JFXPanel()
        browser = initBrowser(jfxPanel!!).join()
        loadContent(browser!!, project)
        toolWindow.component.parent.add(jfxPanel)
    }
}