package io.snyk.plugin.ui.jcef

import com.google.gson.Gson
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.services.SnykApplicationSettingsStateService
import io.snyk.plugin.ui.toolwindow.SnykPluginDisposable
import java.util.concurrent.TimeUnit
import org.awaitility.Awaitility.await
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import snyk.common.lsp.LanguageServerWrapper

/**
 * CP3 (IDE-2181): proves the plugin's fallback settings page Connect control dispatches the
 * `snyk.login` command through the plugin's command bridge to the language server.
 *
 * The proof has two ends, joined by the `window.__ideExecuteCommand__` contract:
 * 1. The fallback resource that IntelliJ actually loads (`html/settings-fallback.html` on the
 *    plugin classpath) wires a Connect button to `__ideExecuteCommand__('snyk.login', [])`. RED
 *    before the CP2->plugin mirror (the pre-mirror copy has no re-auth control).
 * 2. The bridge, given the exact payload that Connect emits (`snyk.login` with no args, D2(a)),
 *    forwards `snyk.login` to the language server with the login timeout.
 */
class SettingsFallbackReauthDispatchTest {
  private val projectMock = mockk<Project>(relaxed = true)
  private val applicationMock = mockk<Application>(relaxed = true)
  private val lsWrapperMock = mockk<LanguageServerWrapper>(relaxed = true)
  private val gson = Gson()

  @Before
  fun setUp() {
    unmockkAll()
    mockkStatic(ApplicationManager::class)
    mockkStatic("io.snyk.plugin.UtilsKt")

    every { ApplicationManager.getApplication() } returns applicationMock
    every { applicationMock.getService(SnykPluginDisposable::class.java) } returns
      SnykPluginDisposable()
    every { pluginSettings() } returns SnykApplicationSettingsStateService()

    mockkObject(LanguageServerWrapper.Companion)
    every { LanguageServerWrapper.getInstance(projectMock) } returns lsWrapperMock
  }

  @After
  fun tearDown() {
    unmockkAll()
  }

  @Test
  fun `fallback resource wires Connect control to dispatch snyk login via the command bridge`() {
    val html =
      javaClass.classLoader
        .getResourceAsStream("html/settings-fallback.html")
        ?.bufferedReader()
        ?.use { it.readText() }
        ?: error("plugin fallback resource html/settings-fallback.html not found on classpath")

    assertTrue(
      "fallback page must expose a Connect re-auth button",
      html.contains("id=\"reauth-button\"") && html.contains(">Connect<"),
    )
    assertTrue(
      "Connect control must dispatch snyk.login through window.__ideExecuteCommand__",
      html.contains("__ideExecuteCommand__('snyk.login'"),
    )
  }

  @Test
  fun `bridge forwards the fallback snyk login payload with no args to the language server`() {
    // Exact request the fallback emits: __ideExecuteCommand__('snyk.login', [], done)
    val payload = gson.toJson(ExecuteCommandRequest(command = "snyk.login", args = emptyList()))

    ExecuteCommandBridge(projectMock).dispatch(payload)

    await().atMost(2, TimeUnit.SECONDS).untilAsserted {
      verify { lsWrapperMock.executeCommandWithArgs("snyk.login", emptyList(), 120_000L) }
    }
  }
}
