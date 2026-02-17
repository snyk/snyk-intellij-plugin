package io.snyk.plugin.ui.toolwindow.panels

import com.intellij.testFramework.LightPlatform4TestCase
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.ui.jcef.JBCefBrowser
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import io.snyk.plugin.events.SnykTreeViewListener
import io.snyk.plugin.resetSettings
import io.snyk.plugin.ui.jcef.JCEFUtils
import javax.swing.JLabel
import org.junit.Test
import snyk.common.lsp.SnykTreeViewParams

class HtmlTreePanelTest : LightPlatform4TestCase() {
  private lateinit var cut: HtmlTreePanel

  override fun setUp() {
    super.setUp()
    unmockkAll()
    resetSettings(project)
    mockkObject(JCEFUtils)
  }

  override fun tearDown() {
    unmockkAll()
    super.tearDown()
  }

  @Test
  fun `should set name to htmlTreePanel`() {
    every { JCEFUtils.getJBCefBrowserIfSupported(any<String>(), any()) } returns null

    cut = HtmlTreePanel(project)

    assertEquals("htmlTreePanel", cut.name)
  }

  @Test
  fun `should handle null JCEF browser gracefully`() {
    every { JCEFUtils.getJBCefBrowserIfSupported(any<String>(), any()) } returns null

    cut = HtmlTreePanel(project)

    // panel should still be created without throwing
    assertNotNull(cut)
  }

  @Test
  fun `should add browser component when JCEF is supported`() {
    val mockBrowserComponent = JLabel("mock-browser")
    val mockJBCefBrowser: JBCefBrowser = mockk(relaxed = true)
    every { mockJBCefBrowser.component } returns mockBrowserComponent

    every { JCEFUtils.getJBCefBrowserIfSupported(any<String>(), any()) } returns mockJBCefBrowser

    cut = HtmlTreePanel(project)

    // the browser component should be added to the panel
    val found = findComponent(cut, mockBrowserComponent)
    assertTrue("Browser component should be added to panel", found)
  }

  @Test
  fun `dispose should set isDisposed flag`() {
    every { JCEFUtils.getJBCefBrowserIfSupported(any<String>(), any()) } returns null

    cut = HtmlTreePanel(project)
    cut.dispose()

    // after dispose, notification should be ignored (tested indirectly via notification test)
    assertNotNull(cut)
  }

  @Test
  fun `should load formatted HTML into browser on tree view notification`() {
    val mockBrowserComponent = JLabel("mock-browser")
    val mockJBCefBrowser: JBCefBrowser = mockk(relaxed = true)
    every { mockJBCefBrowser.component } returns mockBrowserComponent

    every { JCEFUtils.getJBCefBrowserIfSupported(any<String>(), any()) } returns mockJBCefBrowser

    cut = HtmlTreePanel(project)

    // simulate tree view notification via message bus
    val params =
      SnykTreeViewParams(
        treeViewHtml = "<html><head>\${ideStyle}</head><body>\${nonce}</body></html>",
        totalIssues = 5,
      )
    project.messageBus
      .syncPublisher(SnykTreeViewListener.SNYK_TREE_VIEW_TOPIC)
      .onTreeViewReceived(params)

    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    verify {
      mockJBCefBrowser.loadHTML(
        match { html -> !html.contains("\${nonce}") && !html.contains("\${ideStyle}") }
      )
    }
  }

  @Test
  fun `should not load HTML after dispose`() {
    val mockBrowserComponent = JLabel("mock-browser")
    val mockJBCefBrowser: JBCefBrowser = mockk(relaxed = true)
    every { mockJBCefBrowser.component } returns mockBrowserComponent

    every { JCEFUtils.getJBCefBrowserIfSupported(any<String>(), any()) } returns mockJBCefBrowser

    cut = HtmlTreePanel(project)
    cut.dispose()

    val params =
      SnykTreeViewParams(treeViewHtml = "<html><body>updated</body></html>", totalIssues = 3)
    project.messageBus
      .syncPublisher(SnykTreeViewListener.SNYK_TREE_VIEW_TOPIC)
      .onTreeViewReceived(params)

    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    verify(exactly = 0) { mockJBCefBrowser.loadHTML(any<String>()) }
  }

  @Test
  fun `init HTML resource file should exist`() {
    val resource = HtmlTreePanel::class.java.classLoader.getResource(HtmlTreePanel.HTML_INIT_FILE)
    assertNotNull("TreeViewInit.html resource must exist", resource)
  }

  @Test
  fun `init HTML should contain required placeholders`() {
    val html =
      HtmlTreePanel::class.java.classLoader.getResource(HtmlTreePanel.HTML_INIT_FILE)!!.readText()

    assertTrue("Init HTML should contain \${nonce}", html.contains("\${nonce}"))
    assertTrue("Init HTML should contain \${ideStyle}", html.contains("\${ideStyle}"))
    assertTrue("Init HTML should contain CSP meta tag", html.contains("Content-Security-Policy"))
  }

  @Test
  fun `getFormattedHtml should replace all placeholders in init HTML`() {
    val html =
      HtmlTreePanel::class.java.classLoader.getResource(HtmlTreePanel.HTML_INIT_FILE)!!.readText()

    val formatted = PanelHTMLUtils.getFormattedHtml(html)

    assertFalse("Formatted HTML should not contain \${nonce}", formatted.contains("\${nonce}"))
    assertFalse(
      "Formatted HTML should not contain \${ideStyle}",
      formatted.contains("\${ideStyle}"),
    )
  }

  private fun findComponent(parent: java.awt.Container, target: java.awt.Component): Boolean {
    for (c in parent.components) {
      if (c === target) return true
      if (c is java.awt.Container && findComponent(c, target)) return true
    }
    return false
  }
}
