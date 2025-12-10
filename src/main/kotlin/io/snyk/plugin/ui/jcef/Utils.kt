package io.snyk.plugin.ui.jcef

import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefBrowserBuilder
import com.intellij.ui.jcef.JBCefClient
import com.intellij.util.ui.UIUtil
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
     * @param offScreenRendering Use off-screen rendering (default: true). Set to false for keyboard/tab navigation support.
     */
    fun createBrowser(offScreenRendering: Boolean = true): Pair<JBCefClient, JBCefBrowser> {
        val cefClient = JBCefApp.getInstance().createClient()
        cefClient.setProperty("JS_QUERY_POOL_SIZE", 1)

        // Get IDE background color
        val bgColor = UIUtil.getPanelBackground()
        val bgHex = bgColor.toHex()

        val jbCefBrowser = JBCefBrowserBuilder()
            .setClient(cefClient)
            .setEnableOpenDevToolsMenuItem(false)
            .setMouseWheelEventEnable(true)
            .setOffScreenRendering(offScreenRendering)
            .setUrl("about:blank")
            .build()
        jbCefBrowser.setOpenLinksInExternalBrowser(true)

        // Set browser component background to match IDE theme
        jbCefBrowser.component.background = bgColor

        // Load initial HTML with IDE background to prevent white flash
        val initHtml = "<!DOCTYPE html><html><head><style>html,body{margin:0;padding:0;background:$bgHex;}</style></head><body></body></html>"
        jbCefBrowser.loadHTML(initHtml)

        // For non-offscreen rendering, enable focus traversal for tab navigation
        if (!offScreenRendering) {
            jbCefBrowser.component.isFocusable = true
            cefClient.addFocusHandler(object : CefFocusHandlerAdapter() {
                override fun onSetFocus(browser: CefBrowser?, source: CefFocusHandler.FocusSource?): Boolean {
                    return false
                }
            }, jbCefBrowser.cefBrowser)
        }

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

        // Create a new browser for each panel - they can't share a single browser
        // as loading new HTML in one panel would clear the other
        val (cefClient, jbCefBrowser) = createBrowser()

        for (loadHandlerGenerator in loadHandlerGenerators) {
            val loadHandler = loadHandlerGenerator(jbCefBrowser)
            cefClient.addLoadHandler(loadHandler, jbCefBrowser.cefBrowser)
        }
        jbCefBrowser.loadHTML(html, jbCefBrowser.cefBrowser.url)

        return jbCefBrowser
    }
}
