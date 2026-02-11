package snyk.common

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.mockkObject
import io.snyk.plugin.Severity
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.resetSettings
import org.junit.Test
import snyk.common.lsp.LanguageServerWrapper

class AnnotatorCommonIntegTest : BasePlatformTestCase() {

  private lateinit var languageServerWrapperMock: LanguageServerWrapper

  override fun setUp() {
    super.setUp()

    // Mock LanguageServerWrapper and its methods FIRST
    mockkObject(LanguageServerWrapper.Companion)
    languageServerWrapperMock = mockk(relaxed = true)
    every { LanguageServerWrapper.getInstance(project) } returns languageServerWrapperMock
    justRun {
      languageServerWrapperMock.dispose()
    } // Prevent real dispose logic during project teardown
    justRun {
      languageServerWrapperMock.shutdown()
    } // Prevent real shutdown logic if called directly or by dispose, or by resetSettings
    // Add any other LSW methods called by resetSettings if needed, though relaxed=true might cover
    // them

    // THEN call resetSettings, which might use LSW.getInstance()
    resetSettings(project)
  }

  override fun tearDown() {
    resetSettings(project)
    super.tearDown()
  }

  @Test
  fun `test annotation filtered by severity settings`() {
    val annotatorCommon = AnnotatorCommon(project)
    val control = annotatorCommon.isSeverityToShow(Severity.HIGH)
    assertTrue(control)

    pluginSettings().highSeverityEnabled = false
    val actual = annotatorCommon.isSeverityToShow(Severity.HIGH)
    assertFalse(actual)
  }
}
