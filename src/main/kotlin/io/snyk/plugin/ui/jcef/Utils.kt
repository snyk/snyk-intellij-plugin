package io.snyk.plugin.ui.jcef

import com.intellij.openapi.application.invokeLater
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefBrowserBuilder
import com.intellij.ui.jcef.JBCefClient
import io.snyk.plugin.ui.SnykBalloonNotificationHelper
import org.cef.handler.CefLoadHandlerAdapter
import java.awt.Color
import java.security.SecureRandom
import java.util.Base64

typealias LoadHandlerGenerator = (jbCefBrowser: JBCefBrowser) -> CefLoadHandlerAdapter

/**
 * Extension function to convert Color to CSS hex format.
 */
fun Color.toHex(): String = JCEFUtils.colorToHex(this)

object JCEFUtils {
    private val secureRandom = SecureRandom()

    /**
     * Generates a cryptographically secure nonce for CSP.
     * Uses URL-safe Base64 encoding without padding to avoid special characters.
     */
    fun generateNonce(): String {
        val bytes = ByteArray(16)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    /**
     * Converts a Color to CSS hex format.
     */
    fun colorToHex(color: Color?): String {
        if (color == null) return ""
        return String.format("#%02x%02x%02x", color.red, color.green, color.blue)
    }

    /**
     * Creates a new JCEF browser with standard configuration.
     * The caller is responsible for disposing the browser.
     * @param offScreenRendering Use off-screen rendering (default: true). Set to true for lightweight rendering without native window.
     */
    fun createBrowser(offScreenRendering: Boolean = true): Pair<JBCefClient, JBCefBrowser> {
        val cefClient = JBCefApp.getInstance().createClient()
        cefClient.setProperty("JS_QUERY_POOL_SIZE", 1)

        val jbCefBrowser = JBCefBrowserBuilder()
            .setClient(cefClient)
            .setEnableOpenDevToolsMenuItem(false)
            .setMouseWheelEventEnable(true)
            .setOffScreenRendering(offScreenRendering)
            .setUrl("about:blank")
            .build()
        jbCefBrowser.setOpenLinksInExternalBrowser(true)

        return Pair(cefClient, jbCefBrowser)
    }

    /**
     * Creates a JCEF browser with load handlers but WITHOUT loading HTML.
     * Use this when you want to defer HTML loading to avoid EDT blocking.
     * Call loadHTML() separately, preferably via invokeLater.
     *
     * @return The browser instance, or null if JCEF is not supported
     */
    fun getJBCefBrowserComponentIfSupported(
        loadHandlerGenerators: List<LoadHandlerGenerator>,
    ): JBCefBrowser? {
        if (!JBCefApp.isSupported()) {
            SnykBalloonNotificationHelper.showWarn("JCEF is not supported on this platform, we cannot display issue details", null)
            return null
        }

        val (cefClient, jbCefBrowser) = createBrowser()

        for (loadHandlerGenerator in loadHandlerGenerators) {
            val loadHandler = loadHandlerGenerator(jbCefBrowser)
            cefClient.addLoadHandler(loadHandler, jbCefBrowser.cefBrowser)
        }

        return jbCefBrowser
    }

    /**
     * Creates a JCEF browser and loads HTML content.
     * HTML loading is deferred via invokeLater to avoid blocking the EDT during browser initialization.
     *
     * @param html The HTML content to load
     * @param loadHandlerGenerators List of load handler generators to attach to the browser
     * @return The browser instance, or null if JCEF is not supported
     */
    fun getJBCefBrowserIfSupported(
        html: String,
        loadHandlerGenerators: List<LoadHandlerGenerator>,
    ): JBCefBrowser? {
        val jbCefBrowser = getJBCefBrowserComponentIfSupported(loadHandlerGenerators) ?: return null

        // Defer HTML loading to next EDT cycle to avoid blocking during panel construction
        invokeLater {
            jbCefBrowser.loadHTML(html, jbCefBrowser.cefBrowser.url)
        }

        return jbCefBrowser
    }
}
