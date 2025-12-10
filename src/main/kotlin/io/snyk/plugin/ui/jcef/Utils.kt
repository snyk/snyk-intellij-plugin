package io.snyk.plugin.ui.jcef

import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefBrowserBuilder
import com.intellij.ui.jcef.JBCefClient
import io.snyk.plugin.ui.SnykBalloonNotificationHelper
import org.cef.browser.CefBrowser
import org.cef.handler.CefFocusHandler
import org.cef.handler.CefFocusHandlerAdapter
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
    private var jbCefPair: Pair<JBCefClient, JBCefBrowser>? = null

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
     * @param enableDevTools Whether to enable the dev tools menu (default: false)
     */
    fun createBrowser(enableDevTools: Boolean = false): Pair<JBCefClient, JBCefBrowser> {
        val cefClient = JBCefApp.getInstance().createClient()
        cefClient.setProperty("JS_QUERY_POOL_SIZE", 1)
        val jbCefBrowser = JBCefBrowserBuilder()
            .setClient(cefClient)
            .setEnableOpenDevToolsMenuItem(enableDevTools)
            .setMouseWheelEventEnable(true)
            .setOffScreenRendering(false) // Use native window for better keyboard/tab support
            .setUrl("about:blank")
            .build()
        jbCefBrowser.setOpenLinksInExternalBrowser(true)
        
        // Enable focus traversal for tab navigation
        jbCefBrowser.component.isFocusable = true
        
        // Add focus handler to properly manage focus between IDE and browser
        cefClient.addFocusHandler(object : CefFocusHandlerAdapter() {
            override fun onSetFocus(browser: CefBrowser?, source: CefFocusHandler.FocusSource?): Boolean {
                // Return false to allow the browser to receive focus
                return false
            }
        }, jbCefBrowser.cefBrowser)
        
        return Pair(cefClient, jbCefBrowser)
    }

    fun getJBCefBrowserIfSupported(
        html: String,
        loadHandlerGenerators: List<LoadHandlerGenerator>,
    ): JBCefBrowser? {
        if (!JBCefApp.isSupported()) {
            SnykBalloonNotificationHelper.showWarn("JCEF is not supported on this platform, we cannot display issue details", null)
            return null
        }

        val (cefClient, jbCefBrowser) = getCachedBrowser()

        for (loadHandlerGenerator in loadHandlerGenerators) {
            val loadHandler = loadHandlerGenerator(jbCefBrowser)
            cefClient.addLoadHandler(loadHandler, jbCefBrowser.cefBrowser)
        }
        jbCefBrowser.loadHTML(html, jbCefBrowser.cefBrowser.url)

        return jbCefBrowser
    }

    private fun getCachedBrowser(): Pair<JBCefClient, JBCefBrowser> {
        jbCefPair?.let { return it }
        val pair = createBrowser(enableDevTools = false)
        jbCefPair = pair
        return pair
    }
}
