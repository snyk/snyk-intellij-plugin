package io.snyk.plugin.ui.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.testFramework.LightPlatform4TestCase
import com.intellij.testFramework.replaceService
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import io.snyk.plugin.getSnykToolWindowPanel
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.ui.toolwindow.SnykToolWindowPanel
import org.junit.Test
import snyk.common.lsp.LanguageServerWrapper

class SnykLogoutActionTest : LightPlatform4TestCase() {

  private val languageServerWrapperMock = mockk<LanguageServerWrapper>(relaxed = true)
  private val toolWindowPanelMock = mockk<SnykToolWindowPanel>(relaxed = true)
  private lateinit var action: SnykLogoutAction

  override fun setUp() {
    super.setUp()
    unmockkAll()
    mockkStatic("io.snyk.plugin.UtilsKt")
    every { getSnykToolWindowPanel(any()) } returns toolWindowPanelMock
    project.replaceService(LanguageServerWrapper::class.java, languageServerWrapperMock, project)
    action = SnykLogoutAction()
  }

  override fun tearDown() {
    pluginSettings().token = null
    project.replaceService(
      LanguageServerWrapper::class.java,
      LanguageServerWrapper(project),
      project,
    )
    unmockkAll()
    super.tearDown()
  }

  private fun actionEvent(withProject: Boolean = true): AnActionEvent {
    val presentation = Presentation()
    return mockk<AnActionEvent>(relaxed = true).also {
      every { it.project } returns if (withProject) project else null
      every { it.presentation } returns presentation
    }
  }

  @Test
  fun `update enables the action when a token is present`() {
    pluginSettings().token = "a-token"
    val event = actionEvent()

    action.update(event)

    assertTrue(event.presentation.isEnabled)
  }

  @Test
  fun `update disables the action when the token is blank`() {
    pluginSettings().token = ""
    val event = actionEvent()

    action.update(event)

    assertFalse(event.presentation.isEnabled)
  }

  @Test
  fun `update disables the action when there is no project`() {
    pluginSettings().token = "a-token"
    val event = actionEvent(withProject = false)

    action.update(event)

    assertFalse(event.presentation.isEnabled)
  }

  @Test
  fun `actionPerformed logs out of the language server and clears the local token`() {
    pluginSettings().token = "a-stale-token"

    action.actionPerformed(actionEvent())

    // actionPerformed runs on a pooled thread; verify with a timeout.
    verify(timeout = 2000) { languageServerWrapperMock.logout() }

    val deadline = System.currentTimeMillis() + 2000
    while (System.currentTimeMillis() < deadline && !pluginSettings().token.isNullOrEmpty()) {
      Thread.sleep(20)
    }
    assertTrue(pluginSettings().token.isNullOrEmpty())
  }
}
