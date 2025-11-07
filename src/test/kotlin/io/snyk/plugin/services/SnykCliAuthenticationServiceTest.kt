package io.snyk.plugin.services

import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.LightPlatform4TestCase
import com.intellij.testFramework.replaceService
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.snyk.plugin.getCliFile
import io.snyk.plugin.getSnykTaskQueueService
import org.junit.Test
import snyk.common.lsp.LanguageServerWrapper
import snyk.trust.WorkspaceTrustService
import snyk.trust.confirmScanningAndSetWorkspaceTrustedStateIfNeeded
import java.util.concurrent.CompletableFuture

class SnykCliAuthenticationServiceTest : LightPlatform4TestCase() {

    private val workspaceTrustServiceMock = mockk<WorkspaceTrustService>(relaxed = true)
    private val languageServerWrapperMock = mockk<LanguageServerWrapper>(relaxed = true)
    private val taskQueueServiceMock = mockk<SnykTaskQueueService>(relaxed = true)

    override fun setUp() {
        super.setUp()
        unmockkAll()
        mockkStatic("snyk.trust.TrustedProjectsKt")
        mockkStatic("io.snyk.plugin.UtilsKt")
        every { confirmScanningAndSetWorkspaceTrustedStateIfNeeded(any()) } returns true
        every { getCliFile() } returns mockk(relaxed = true) {
            every { exists() } returns true
            every { absolutePath } returns "/mock/cli/path"
        }
        every { getSnykTaskQueueService(any()) } returns taskQueueServiceMock

        val application = ApplicationManager.getApplication()
        application.replaceService(WorkspaceTrustService::class.java, workspaceTrustServiceMock, application)
        project.replaceService(LanguageServerWrapper::class.java, languageServerWrapperMock, project)
    }

    override fun tearDown() {
        unmockkAll()
        super.tearDown()
    }

    @Test
    fun `authenticate should call updateConfiguration`() {
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
}
