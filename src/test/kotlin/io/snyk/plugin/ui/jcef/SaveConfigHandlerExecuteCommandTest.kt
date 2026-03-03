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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.awaitility.Awaitility.await
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import snyk.common.lsp.LanguageServerWrapper

class SaveConfigHandlerExecuteCommandTest {
  private lateinit var handler: SaveConfigHandler
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

    handler = SaveConfigHandler(projectMock, onModified = {})
  }

  @After
  fun tearDown() {
    unmockkAll()
  }

  @Test
  fun `dispatchSettingsCommand routes snyk login to language server with auth args`() {
    val request =
      TreeViewCommandRequest(
        command = "snyk.login",
        args = listOf("oauth2", "https://api.snyk.io", false),
      )
    val payload = gson.toJson(request)

    handler.dispatchSettingsCommand(payload)

    await().atMost(2, TimeUnit.SECONDS).untilAsserted {
      verify {
        lsWrapperMock.executeCommandWithArgs(
          "snyk.login",
          listOf("oauth2", "https://api.snyk.io", false),
        )
      }
    }
  }

  @Test
  fun `dispatchSettingsCommand routes snyk logout to language server`() {
    val request = TreeViewCommandRequest(command = "snyk.logout", args = emptyList())
    val payload = gson.toJson(request)

    handler.dispatchSettingsCommand(payload)

    await().atMost(2, TimeUnit.SECONDS).untilAsserted {
      verify { lsWrapperMock.executeCommandWithArgs("snyk.logout", emptyList()) }
    }
  }

  @Test
  fun `dispatchSettingsCommand ignores empty command`() {
    val request = TreeViewCommandRequest(command = "", args = emptyList())
    val payload = gson.toJson(request)

    handler.dispatchSettingsCommand(payload)

    await().atMost(2, TimeUnit.SECONDS).untilAsserted {
      verify(exactly = 0) { lsWrapperMock.executeCommandWithArgs(any(), any()) }
    }
  }

  @Test
  fun `dispatchSettingsCommand invokes callback when callbackId present`() {
    every { lsWrapperMock.executeCommandWithArgs("snyk.login", any()) } returns "auth-result"

    val latch = CountDownLatch(1)
    var receivedCallbackId: String? = null
    var receivedResult: String? = null

    val request =
      TreeViewCommandRequest(
        command = "snyk.login",
        args = listOf("oauth2", "https://api.snyk.io", false),
        callbackId = "__cb_1",
      )
    val payload = gson.toJson(request)

    handler.dispatchSettingsCommand(payload) { callbackId, escaped ->
      receivedCallbackId = callbackId
      receivedResult = escaped
      latch.countDown()
    }

    assertTrue("Callback should be invoked within timeout", latch.await(2, TimeUnit.SECONDS))
    assertEquals("__cb_1", receivedCallbackId)
    assertTrue(receivedResult!!.contains("auth-result"))
  }

  @Test
  fun `dispatchSettingsCommand does not throw for malformed json`() {
    handler.dispatchSettingsCommand("not-valid-json")
    // should not throw
  }
}
