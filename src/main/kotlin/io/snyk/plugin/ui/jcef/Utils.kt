package io.snyk.plugin.ui.jcef

import com.intellij.openapi.diagnostic.Logger
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefBrowserBuilder
import com.intellij.ui.jcef.JBCefClient
import io.snyk.plugin.ui.SnykBalloonNotificationHelper
import org.cef.handler.CefLoadHandlerAdapter

typealias LoadHandlerGenerator = (jbCefBrowser: JBCefBrowser) -> CefLoadHandlerAdapter

object JCEFUtils {
    private val jbCefPair : Pair<JBCefClient, JBCefBrowser>? = null

    private val logger = Logger.getInstance(JCEFUtils.javaClass)

    fun getJBCefBrowserIfSupported(
        html: String,
        loadHandlerGenerators: List<LoadHandlerGenerator>,
    ): JBCefBrowser? {
        if (!JBCefApp.isSupported()) {
            SnykBalloonNotificationHelper.showWarn("JCEF is not supported on this platform, we cannot display issue details", null)
            return null
        }

        val (cefClient, jbCefBrowser) = getBrowser()

        for (loadHandlerGenerator in loadHandlerGenerators) {
            val loadHandler = loadHandlerGenerator(jbCefBrowser)
            cefClient.addLoadHandler(loadHandler, jbCefBrowser.cefBrowser)
        }
        jbCefBrowser.loadHTML(html, jbCefBrowser.cefBrowser.url)

        return jbCefBrowser
    }

    private fun getBrowser(): Pair<JBCefClient, JBCefBrowser> {
        if (jbCefPair != null) {
            return jbCefPair
        }
        val cefClient = JBCefApp.getInstance().createClient()
        cefClient.setProperty("JS_QUERY_POOL_SIZE", 1)
        val jbCefBrowser =
            JBCefBrowserBuilder().setClient(cefClient).setEnableOpenDevToolsMenuItem(true)
                .setMouseWheelEventEnable(true)
                .setUrl("about:blank")
                .build()
        jbCefBrowser.setOpenLinksInExternalBrowser(true)
        val jbCefPair = Pair(cefClient, jbCefBrowser)
        return jbCefPair
    }
}
