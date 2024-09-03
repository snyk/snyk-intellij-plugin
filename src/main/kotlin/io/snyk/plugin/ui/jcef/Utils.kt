package io.snyk.plugin.ui.jcef

import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefBrowserBuilder
import org.cef.handler.CefLoadHandlerAdapter
import java.awt.Component

typealias LoadHandlerGenerator = (jbCefBrowser: JBCefBrowser) -> CefLoadHandlerAdapter

object JCEFUtils {
    fun getJBCefBrowserComponentIfSupported(
        html: String,
        loadHandlerGenerators: List<LoadHandlerGenerator>,
    ): Component? {
        if (!JBCefApp.isSupported()) {
            return null
        }
        val cefClient = JBCefApp.getInstance().createClient()
        cefClient.setProperty("JS_QUERY_POOL_SIZE", 1)
        val jbCefBrowser =
            JBCefBrowserBuilder().setClient(cefClient).setEnableOpenDevToolsMenuItem(true)
                .setMouseWheelEventEnable(true).build()
        jbCefBrowser.setOpenLinksInExternalBrowser(true)

        for (loadHandlerGenerator in loadHandlerGenerators) {
            val loadHandler = loadHandlerGenerator(jbCefBrowser)
            cefClient.addLoadHandler(loadHandler, jbCefBrowser.cefBrowser)
        }
        jbCefBrowser.loadHTML(html, jbCefBrowser.cefBrowser.url)

        return jbCefBrowser.component
    }
}
