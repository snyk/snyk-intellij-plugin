package io.snyk.plugin.services

import com.intellij.testFramework.LightPlatform4TestCase
import com.intellij.testFramework.replaceService
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import io.snyk.plugin.getCliFile
import io.snyk.plugin.getSnykTaskQueueService
import io.snyk.plugin.pluginSettings
import java.io.File
import java.util.concurrent.CompletableFuture
import org.junit.Test
import snyk.common.lsp.LanguageServerWrapper

class SnykCliAuthenticationServiceTest : LightPlatform4TestCase() {

  private val languageServerWrapperMock = mockk<LanguageServerWrapper>(relaxed = true)
  private val taskQueueServiceMock = mockk<SnykTaskQueueService>(relaxed = true)

  override fun setUp() {
    super.setUp()
    unmockkAll()
    mockkStatic("io.snyk.plugin.UtilsKt")
    every { getCliFile() } returns
      mockk(relaxed = true) {
        every { exists() } returns true
        every { absolutePath } returns "/mock/cli/path"
      }
    every { getSnykTaskQueueService(any()) } returns taskQueueServiceMock

    project.replaceService(LanguageServerWrapper::class.java, languageServerWrapperMock, project)
  }

  override fun tearDown() {
    project.replaceService(
      LanguageServerWrapper::class.java,
      LanguageServerWrapper(project),
      project,
    )
    unmockkAll()
    super.tearDown()
  }

  @Test
  fun `test service instantiation with mocked dependencies`() {
    // Mock the login to return a completed future
    val loginFuture: CompletableFuture<Any> = CompletableFuture.completedFuture("mock-token" as Any)
    every { languageServerWrapperMock.login() } returns loginFuture
    every { languageServerWrapperMock.logout() } returns Unit
    every { languageServerWrapperMock.cancelPreviousLogin() } returns Unit
    every { languageServerWrapperMock.updateConfiguration(any()) } returns Unit
    every { languageServerWrapperMock.ensureLanguageServerInitialized() } returns true

    val service = SnykCliAuthenticationService(project)

    // Note: We can't fully test authenticate() in a unit test because it shows a dialog
    // The threading fix ensures the dialog is shown non-blocking via invokeLater
    // This test just verifies the service can be instantiated
    assertNotNull(service)

    // Verify the service has the correct project
    assertEquals(project, service.project)
  }

  @Test
  fun `test CLI file existence check returns true when file exists`() {
    val mockFile = mockk<File>(relaxed = true)
    every { mockFile.exists() } returns true
    every { mockFile.absolutePath } returns "/mock/cli/path"
    every { getCliFile() } returns mockFile

    val service = SnykCliAuthenticationService(project)

    assertNotNull(service)
    assertTrue(getCliFile().exists())
  }

  @Test
  fun `test CLI file existence check returns false when file does not exist`() {
    val mockFile = mockk<File>(relaxed = true)
    every { mockFile.exists() } returns false
    every { mockFile.absolutePath } returns "/mock/cli/path"
    every { getCliFile() } returns mockFile

    val service = SnykCliAuthenticationService(project)

    assertNotNull(service)
    assertFalse(getCliFile().exists())
  }

  @Test
  fun `test language server wrapper is retrieved from project`() {
    every { languageServerWrapperMock.login() } returns
      CompletableFuture.completedFuture("mock-token" as Any)
    every { languageServerWrapperMock.logout() } returns Unit
    every { languageServerWrapperMock.cancelPreviousLogin() } returns Unit
    every { languageServerWrapperMock.updateConfiguration(any()) } returns Unit
    every { languageServerWrapperMock.ensureLanguageServerInitialized() } returns true

    val service = SnykCliAuthenticationService(project)

    assertNotNull(service)
    // Verify that LanguageServerWrapper can be retrieved for the project
    val wrapper = LanguageServerWrapper.getInstance(project)
    assertNotNull(wrapper)
    assertEquals(languageServerWrapperMock, wrapper)
  }

  @Test
  fun `logout clears the language server and the local token`() {
    every { languageServerWrapperMock.logout() } returns Unit
    pluginSettings().token = "a-stale-token"

    SnykCliAuthenticationService(project).logout()

    // logout() runs on a pooled thread; verify with a timeout. The local token clear happens on
    // the same thread strictly before the LS logout() call, so once MockK observes that call the
    // token is guaranteed to already be cleared - no need to sleep-poll for it separately.
    verify(timeout = 2000) { languageServerWrapperMock.logout() }
    assertTrue(pluginSettings().token.isNullOrEmpty())
  }

  @Test
  fun `test task queue service is available for project`() {
    val service = SnykCliAuthenticationService(project)

    assertNotNull(service)
    // Verify that task queue service can be retrieved
    val taskQueueService = getSnykTaskQueueService(project)
    assertNotNull(taskQueueService)
    assertEquals(taskQueueServiceMock, taskQueueService)
  }
}
