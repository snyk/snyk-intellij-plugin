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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import snyk.common.lsp.LanguageServerWrapper

class TreeViewBridgeHandlerTest {
  private lateinit var handler: TreeViewBridgeHandler
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

    handler = TreeViewBridgeHandler(projectMock)
  }

  @After
  fun tearDown() {
    unmockkAll()
  }

  @Test
  fun `dispatchCommand should dispatch command to language server`() {
    val request =
      TreeViewCommandRequest(
        command = "snyk.toggleTreeFilter",
        args = listOf("severity", "high", true),
      )
    val payload = gson.toJson(request)

    handler.dispatchCommand(payload)

    verify(timeout = 2000) {
      lsWrapperMock.executeCommandWithArgs(
        "snyk.toggleTreeFilter",
        listOf("severity", "high", true),
      )
    }
  }

  @Test
  fun `dispatchCommand should not throw for malformed json`() {
    handler.dispatchCommand("not-valid-json")
    // should not throw
  }

  @Test
  fun `dispatchCommand should handle empty command`() {
    val request = TreeViewCommandRequest(command = "", args = emptyList())
    val payload = gson.toJson(request)

    handler.dispatchCommand(payload)

    verify(timeout = 2000) { lsWrapperMock.executeCommandWithArgs("", emptyList()) }
  }

  @Test
  fun `dispatchCommand should invoke callback when callbackId is present and result is non-null`() {
    every { lsWrapperMock.executeCommandWithArgs(any(), any()) } returns "<div>chunk</div>"

    val latch = CountDownLatch(1)
    var receivedCallbackId: String? = null
    var receivedResult: String? = null

    val request =
      TreeViewCommandRequest(
        command = "snyk.getTreeViewIssueChunk",
        args = listOf("req1", "/path", "code", 0, 10),
        callbackId = "__cb_1",
      )
    val payload = gson.toJson(request)

    handler.dispatchCommand(payload) { callbackId, escaped ->
      receivedCallbackId = callbackId
      receivedResult = escaped
      latch.countDown()
    }

    assertTrue("Callback should be invoked within timeout", latch.await(2, TimeUnit.SECONDS))
    assertEquals("__cb_1", receivedCallbackId)
    assertNotNull(receivedResult)
    assertTrue(receivedResult!!.contains("chunk"))
  }

  @Test
  fun `dispatchCommand should not invoke callback when result is null`() {
    every { lsWrapperMock.executeCommandWithArgs(any(), any()) } returns null

    var callbackInvoked = false
    val request =
      TreeViewCommandRequest(
        command = "snyk.getTreeViewIssueChunk",
        args = listOf("req1"),
        callbackId = "__cb_2",
      )
    val payload = gson.toJson(request)

    handler.dispatchCommand(payload) { _, _ -> callbackInvoked = true }

    Thread.sleep(500)
    assertEquals(false, callbackInvoked)
  }

  @Test
  fun `dispatchCommand should not invoke callback when callbackId is absent`() {
    every { lsWrapperMock.executeCommandWithArgs(any(), any()) } returns "result"

    var callbackInvoked = false
    val request =
      TreeViewCommandRequest(command = "snyk.navigateToRange", args = listOf("/file.kt"))
    val payload = gson.toJson(request)

    handler.dispatchCommand(payload) { _, _ -> callbackInvoked = true }

    Thread.sleep(500)
    assertEquals(false, callbackInvoked)
  }

  @Test
  fun `TreeViewCommandRequest deserialization should work correctly`() {
    val json =
      """{"command":"snyk.navigateToRange","args":["/file.kt",{"start":{"line":1,"character":0}}],"callbackId":null}"""
    val request = gson.fromJson(json, TreeViewCommandRequest::class.java)

    assertEquals("snyk.navigateToRange", request.command)
    assertEquals(2, request.args.size)
    assertNotNull(request.args[0])
  }
}
