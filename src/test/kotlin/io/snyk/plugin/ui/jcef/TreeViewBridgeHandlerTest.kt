package io.snyk.plugin.ui.jcef

import com.google.gson.Gson
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.ui.jcef.JBCefJSQuery
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
import java.util.concurrent.atomic.AtomicBoolean
import org.awaitility.Awaitility.await
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
  fun `dispatchCommand should reject empty command via allowlist`() {
    val request = TreeViewCommandRequest(command = "", args = emptyList())
    val payload = gson.toJson(request)

    handler.dispatchCommand(payload)

    await().atMost(2, TimeUnit.SECONDS).untilAsserted {
      verify(exactly = 0) { lsWrapperMock.executeCommandWithArgs(any(), any()) }
    }
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
  fun `dispatchCommand should invoke callback with null when result is null to prevent JS callback leak`() {
    every { lsWrapperMock.executeCommandWithArgs(any(), any()) } returns null

    val latch = CountDownLatch(1)
    var receivedResult: String? = "not-called"
    val request =
      TreeViewCommandRequest(
        command = "snyk.navigateToRange",
        args = listOf("/file.kt"),
        callbackId = "__cb_2",
      )
    val payload = gson.toJson(request)

    handler.dispatchCommand(payload) { _, escaped ->
      receivedResult = escaped
      latch.countDown()
    }

    assertTrue("Callback should be invoked even for null result", latch.await(2, TimeUnit.SECONDS))
    assertEquals("null", receivedResult)
  }

  @Test
  fun `dispatchCommand should not invoke callback when callbackId is absent`() {
    every { lsWrapperMock.executeCommandWithArgs(any(), any()) } returns "result"

    val callbackInvoked = AtomicBoolean(false)
    val request =
      TreeViewCommandRequest(command = "snyk.navigateToRange", args = listOf("/file.kt"))
    val payload = gson.toJson(request)

    handler.dispatchCommand(payload) { _, _ -> callbackInvoked.set(true) }

    // Wait for async dispatch to complete, then verify callback was not invoked
    await().atMost(2, TimeUnit.SECONDS).untilAsserted {
      verify { lsWrapperMock.executeCommandWithArgs("snyk.navigateToRange", listOf("/file.kt")) }
    }
    assertEquals(false, callbackInvoked.get())
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

  @Test
  fun `TreeViewCommandRequest defaults should be correct`() {
    val request = TreeViewCommandRequest()

    assertEquals("", request.command)
    assertEquals(emptyList<Any>(), request.args)
    assertEquals(null, request.callbackId)
  }

  @Test
  fun `dispatchCommand should invoke error callback when language server throws`() {
    every { lsWrapperMock.executeCommandWithArgs(any(), any()) } throws RuntimeException("LS error")

    val latch = CountDownLatch(1)
    var receivedResult: String? = null
    val request =
      TreeViewCommandRequest(
        command = "snyk.setNodeExpanded",
        args = listOf("arg1"),
        callbackId = "__cb_err",
      )
    val payload = gson.toJson(request)

    handler.dispatchCommand(payload) { _, result ->
      receivedResult = result
      latch.countDown()
    }

    assertTrue("Callback should be invoked with error", latch.await(2, TimeUnit.SECONDS))
    assertTrue("Result should contain error", receivedResult!!.contains("error"))
    verify { lsWrapperMock.executeCommandWithArgs("snyk.setNodeExpanded", listOf("arg1")) }
  }

  @Test
  fun `dispatchCommand should not throw when language server throws and no callback`() {
    every { lsWrapperMock.executeCommandWithArgs(any(), any()) } throws RuntimeException("LS error")

    val request = TreeViewCommandRequest(command = "snyk.setNodeExpanded", args = listOf("arg1"))
    val payload = gson.toJson(request)

    // should not throw
    handler.dispatchCommand(payload)

    await().atMost(2, TimeUnit.SECONDS).untilAsserted {
      verify { lsWrapperMock.executeCommandWithArgs("snyk.setNodeExpanded", listOf("arg1")) }
    }
  }

  @Test
  fun `dispatchCommand should not invoke callback when callbackExecutor is null`() {
    every { lsWrapperMock.executeCommandWithArgs(any(), any()) } returns "result"

    val request =
      TreeViewCommandRequest(
        command = "snyk.setNodeExpanded",
        args = emptyList(),
        callbackId = "__cb_3",
      )
    val payload = gson.toJson(request)

    // pass null callbackExecutor — should not throw
    handler.dispatchCommand(payload, null)

    await().atMost(2, TimeUnit.SECONDS).untilAsserted {
      verify { lsWrapperMock.executeCommandWithArgs("snyk.setNodeExpanded", emptyList()) }
    }
  }

  @Test
  fun `dispatchCommand should handle concurrent calls without errors`() {
    every { lsWrapperMock.executeCommandWithArgs(any(), any()) } returns "ok"

    val latch = CountDownLatch(3)
    val errors = mutableListOf<Exception>()

    repeat(3) { i ->
      Thread {
          try {
            val request =
              TreeViewCommandRequest(command = "snyk.setNodeExpanded", args = listOf("arg$i"))
            handler.dispatchCommand(gson.toJson(request))
          } catch (e: Exception) {
            synchronized(errors) { errors.add(e) }
          } finally {
            latch.countDown()
          }
        }
        .start()
    }

    assertTrue("All dispatches should complete", latch.await(3, TimeUnit.SECONDS))
    assertTrue("No errors expected, got: $errors", errors.isEmpty())
  }

  @Test
  fun `dispatchCommand should reject commands not in the allowlist and invoke error callback`() {
    val request =
      TreeViewCommandRequest(command = "snyk.logout", args = emptyList(), callbackId = "__cb_99")
    val payload = gson.toJson(request)

    val latch = CountDownLatch(1)
    var receivedResult: String? = null
    handler.dispatchCommand(payload) { _, result ->
      receivedResult = result
      latch.countDown()
    }

    assertTrue("Callback should be invoked with error", latch.await(2, TimeUnit.SECONDS))
    assertTrue("Result should contain error", receivedResult!!.contains("error"))
    verify(exactly = 0) { lsWrapperMock.executeCommandWithArgs(any(), any()) }
  }

  @Test
  fun `dispatchCommand should reject callbackId with non-alphanumeric characters and not invoke callback`() {
    every { lsWrapperMock.executeCommandWithArgs(any(), any()) } returns "result"

    val maliciousCallbackId = "'];alert('xss');//"
    val request =
      TreeViewCommandRequest(
        command = "snyk.setNodeExpanded",
        args = emptyList(),
        callbackId = maliciousCallbackId,
      )
    val payload = gson.toJson(request)

    val callbackInvoked = AtomicBoolean(false)
    handler.dispatchCommand(payload) { _, _ -> callbackInvoked.set(true) }

    await().atMost(2, TimeUnit.SECONDS).untilAsserted {
      verify(exactly = 0) { lsWrapperMock.executeCommandWithArgs(any(), any()) }
    }
    // callback must NOT be invoked for unsafe callbackIds — prevents JS injection
    assertEquals(false, callbackInvoked.get())
  }

  @Test
  fun `dispatchCommand should accept all allowed tree view commands`() {
    every { lsWrapperMock.executeCommandWithArgs(any(), any()) } returns "ok"

    val allowedCommands =
      listOf(
        "snyk.navigateToRange",
        "snyk.toggleTreeFilter",
        "snyk.getTreeViewIssueChunk",
        "snyk.setNodeExpanded",
        "snyk.showScanErrorDetails",
        "snyk.updateFolderConfig",
      )

    val latch = CountDownLatch(allowedCommands.size)

    for (cmd in allowedCommands) {
      val request = TreeViewCommandRequest(command = cmd, args = emptyList())
      handler.dispatchCommand(gson.toJson(request)) { _, _ -> latch.countDown() }
    }

    verify(timeout = 2000, exactly = allowedCommands.size) {
      lsWrapperMock.executeCommandWithArgs(any(), any())
    }
  }

  @Test
  fun `buildBridgeScript should contain ideExecuteCommand bridge`() {
    val mockQuery: JBCefJSQuery = mockk(relaxed = true)
    every { mockQuery.inject(any()) } returns "window.cefQuery_inject(payload)"

    val script = handler.buildBridgeScript(mockQuery)

    assertTrue(
      "Script should define __ideExecuteCommand__",
      script.contains("__ideExecuteCommand__"),
    )
    assertTrue("Script should define __ideCallbacks__", script.contains("__ideCallbacks__"))
    assertTrue("Script should contain injected query", script.contains("cefQuery_inject"))
  }

  @Test
  fun `ALLOWED_COMMANDS should contain expected commands`() {
    val expected =
      setOf(
        "snyk.navigateToRange",
        "snyk.toggleTreeFilter",
        "snyk.getTreeViewIssueChunk",
        "snyk.setNodeExpanded",
        "snyk.showScanErrorDetails",
        "snyk.updateFolderConfig",
      )
    assertEquals(expected, TreeViewBridgeHandler.ALLOWED_COMMANDS)
  }

  @Test
  fun `TreeViewCommandRequest deserialization with missing fields should use defaults`() {
    val json = """{"command":"snyk.test"}"""
    val request = gson.fromJson(json, TreeViewCommandRequest::class.java)

    assertEquals("snyk.test", request.command)
    assertNotNull(request.args)
    assertEquals(null, request.callbackId)
  }
}
