@file:Suppress("FunctionName")

package snyk.trust

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.replaceService
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import java.nio.file.Paths

class WorkspaceTrustServiceIntegrationTest : BasePlatformTestCase() {

  private val workspaceTrustSettingsMock = mockk<WorkspaceTrustSettings>()
  private lateinit var cut: WorkspaceTrustService

  private class IntegTestDisposable : Disposable {
    override fun dispose() = Unit
  }

  public override fun setUp() {
    super.setUp()
    unmockkAll()

    val application = ApplicationManager.getApplication()
    application.replaceService(
      WorkspaceTrustSettings::class.java,
      workspaceTrustSettingsMock,
      IntegTestDisposable(),
    )

    cut = WorkspaceTrustService()
  }

  fun `test isPathTrusted should return false if no trusted path in settings available`() {
    every { workspaceTrustSettingsMock.getTrustedPaths() } returns listOf()

    val path = Paths.get("/project")

    assertFalse(cut.isPathTrusted(path))
  }

  fun `test isPathTrusted should return true if trusted path in settings available`() {
    every { workspaceTrustSettingsMock.getTrustedPaths() } returns listOf("/project")

    val path = Paths.get("/project")

    assertTrue(cut.isPathTrusted(path))
  }
}
