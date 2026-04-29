package snyk.common

import com.intellij.openapi.components.service
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import io.snyk.plugin.Severity
import io.snyk.plugin.events.SnykFolderConfigListener
import io.snyk.plugin.events.SnykProductsOrSeverityListener
import io.snyk.plugin.events.SnykSettingsListener
import io.snyk.plugin.fromPathToUriString
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.refreshAnnotationsForOpenFiles
import io.snyk.plugin.resetSettings
import java.nio.file.Paths
import org.eclipse.lsp4j.WorkspaceFolder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import snyk.common.lsp.LanguageServerWrapper
import snyk.common.lsp.settings.ConfigSetting
import snyk.common.lsp.settings.FolderConfigSettings
import snyk.common.lsp.settings.LsFolderSettingsKeys
import snyk.common.lsp.settings.LspFolderConfig

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
    val psiFile = myFixture.configureByText("Test.java", "class Test {}")
    assertTrue(annotatorCommon.isSeverityToShow(Severity.HIGH, psiFile))

    pluginSettings().highSeverityEnabled = false
    assertFalse(annotatorCommon.isSeverityToShow(Severity.HIGH, psiFile))
  }

  @Test
  fun `hasOnlyOneSeverityTreeFilterActive matches hasOnlyOneSeverityEnabled when no workspace folders`() {
    every { languageServerWrapperMock.getWorkspaceFoldersFromRoots(any(), any()) } returns
      emptySet()
    every { languageServerWrapperMock.configuredWorkspaceFolders } returns mutableSetOf()

    val ps = pluginSettings()
    ps.criticalSeverityEnabled = false
    ps.highSeverityEnabled = false
    ps.mediumSeverityEnabled = false
    ps.lowSeverityEnabled = true

    assertEquals(ps.hasOnlyOneSeverityEnabled(), ps.hasOnlyOneSeverityTreeFilterActive(project))
  }

  @Test
  fun `isSeverityToShow treats UNKNOWN as always visible`() {
    val annotatorCommon = AnnotatorCommon(project)
    val psiFile = myFixture.configureByText("Unknown.java", "class Unknown {}")
    pluginSettings().highSeverityEnabled = false
    assertTrue(annotatorCommon.isSeverityToShow(Severity.UNKNOWN, psiFile))
  }

  @Test
  fun `isSeverityToShow uses folder severity override when file is under workspace`() {
    val base = project.basePath ?: error("basePath required")
    val normalizedBase = Paths.get(base).normalize().toAbsolutePath().toString()
    val workspaceFolder =
      WorkspaceFolder().apply {
        uri = normalizedBase.fromPathToUriString()
        name = "root"
      }
    every { languageServerWrapperMock.getWorkspaceFoldersFromRoots(any(), any()) } returns
      setOf(workspaceFolder)
    every { languageServerWrapperMock.configuredWorkspaceFolders } returns
      mutableSetOf(workspaceFolder)

    val fcs = project.service<FolderConfigSettings>()
    fcs.clear()
    try {
      fcs.addFolderConfig(
        LspFolderConfig(
          folderPath = normalizedBase,
          settings =
            mapOf(LsFolderSettingsKeys.SEVERITY_FILTER_HIGH to ConfigSetting(value = false)),
        )
      )

      pluginSettings().highSeverityEnabled = true

      val annotatorCommon = AnnotatorCommon(project)
      val psiFile = myFixture.configureByText("UnderRoot.java", "class UnderRoot {}")
      assertFalse(annotatorCommon.isSeverityToShow(Severity.HIGH, psiFile))
    } finally {
      fcs.clear()
    }
  }

  @Test
  fun `initRefreshing subscribes so folder config changes refresh annotations`() {
    mockkStatic("io.snyk.plugin.UtilsKt")
    try {
      justRun { refreshAnnotationsForOpenFiles(project) }
      AnnotatorCommon(project).initRefreshing()
      project.messageBus
        .syncPublisher(SnykProductsOrSeverityListener.SNYK_ENABLEMENT_TOPIC)
        .enablementChanged()
      project.messageBus.syncPublisher(SnykSettingsListener.SNYK_SETTINGS_TOPIC).settingsChanged()
      project.messageBus
        .syncPublisher(SnykFolderConfigListener.SNYK_FOLDER_CONFIG_TOPIC)
        .folderConfigsChanged(true)
      verify(timeout = 5000, exactly = 3) { refreshAnnotationsForOpenFiles(project) }
    } finally {
      unmockkStatic("io.snyk.plugin.UtilsKt")
    }
  }
}
