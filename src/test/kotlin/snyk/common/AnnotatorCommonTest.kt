package snyk.common

import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import io.snyk.plugin.isCliInstalled
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.services.SnykApplicationSettingsStateService
import org.junit.After
import org.junit.Before
import org.junit.Test
import snyk.common.lsp.LanguageServerWrapper

class AnnotatorCommonTest {

  private val projectMock: Project = mockk(relaxed = true)
  private val applicationMock: Application = mockk(relaxed = true)
  private val lsWrapperMock = mockk<LanguageServerWrapper>(relaxed = true)
  private val pluginState = SnykApplicationSettingsStateService()

  @Before
  fun setUp() {
    unmockkAll()
    mockkStatic("io.snyk.plugin.UtilsKt")
    mockkStatic(ApplicationManager::class)
    every { ApplicationManager.getApplication() } returns applicationMock
    every { applicationMock.isDisposed } returns false
    every { projectMock.isDisposed } returns false
    every { pluginSettings() } returns pluginState

    mockkObject(LanguageServerWrapper.Companion)
    every { LanguageServerWrapper.getInstance(projectMock) } returns lsWrapperMock
  }

  @After
  fun tearDown() {
    unmockkAll()
  }

  @Test
  fun `prepareAnnotate skips when CLI is not installed`() {
    every { isCliInstalled() } returns false
    every { lsWrapperMock.isInitialized } returns true

    val psiFile = mockk<PsiFile>(relaxed = true)
    AnnotatorCommon(projectMock).prepareAnnotate(psiFile)

    verify(exactly = 0) { LanguageServerWrapper.getInstance(any()) }
  }

  @Test
  fun `prepareAnnotate skips when language server is not initialized`() {
    every { isCliInstalled() } returns true
    every { lsWrapperMock.isInitialized } returns false

    val psiFile = mockk<PsiFile>(relaxed = true)
    AnnotatorCommon(projectMock).prepareAnnotate(psiFile)

    verify(exactly = 1) { LanguageServerWrapper.getInstance(projectMock) }
    verify(exactly = 1) { lsWrapperMock.isInitialized }
  }
}
