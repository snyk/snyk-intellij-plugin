package io.snyk.plugin.ui.jcef

import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefBrowserBuilder
import org.cef.handler.CefLoadHandlerAdapter
import java.awt.Component


object JCEFUtils {
    fun getJBCefBrowserComponentIfSupported (html: String, loadHandlerGenerator: (jbCefBrowser: JBCefBrowser) -> CefLoadHandlerAdapter): Component? {
        if (!JBCefApp.isSupported()) {
            return null
        }
        val cefClient = JBCefApp.getInstance().createClient()
        cefClient.setProperty("JS_QUERY_POOL_SIZE", 1)
        val jbCefBrowser = JBCefBrowserBuilder().
        setClient(cefClient).
        setEnableOpenDevToolsMenuItem(false).
        setMouseWheelEventEnable(true).
        build()
        jbCefBrowser.setOpenLinksInExternalBrowser(true)

        val loadHandler = loadHandlerGenerator(jbCefBrowser)
        cefClient.addLoadHandler(loadHandler, jbCefBrowser.cefBrowser)

        jbCefBrowser.loadHTML(html, jbCefBrowser.cefBrowser.url)

        return jbCefBrowser.component
    }
}
