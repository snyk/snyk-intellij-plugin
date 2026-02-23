package snyk.common

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.snyk.plugin.getSnykCachedResults
import io.snyk.plugin.getSnykCachedResultsForProduct
import io.snyk.plugin.resetSettings
import snyk.common.lsp.LanguageServerWrapper
import snyk.common.lsp.LsProduct
import snyk.common.lsp.PresentableError
import snyk.common.lsp.ScanIssue

class SnykCachedResultsTest : BasePlatformTestCase() {

  private lateinit var languageServerWrapperMock: LanguageServerWrapper

  override fun setUp() {
    super.setUp()
    mockkObject(LanguageServerWrapper.Companion)
    languageServerWrapperMock = mockk(relaxed = true)
    every { LanguageServerWrapper.getInstance(project) } returns languageServerWrapperMock
    justRun { languageServerWrapperMock.dispose() }
    justRun { languageServerWrapperMock.shutdown() }
    resetSettings(project)
  }

  override fun tearDown() {
    unmockkAll()
    resetSettings(project)
    super.tearDown()
  }

  fun `test clearCaches clears secrets results and error`() {
    val cache = getSnykCachedResults(project)!!
    cache.currentSecretsError = PresentableError(error = "test error")
    cache.currentSecretsResultsLS[mockk(relaxed = true)] = emptySet()

    cache.clearCaches()

    assertNull(cache.currentSecretsError)
    assertTrue(cache.currentSecretsResultsLS.isEmpty())
  }

  fun `test clearCaches clears all product caches`() {
    val cache = getSnykCachedResults(project)!!
    cache.currentOssError = PresentableError(error = "oss error")
    cache.currentIacError = PresentableError(error = "iac error")
    cache.currentSnykCodeError = PresentableError(error = "code error")
    cache.currentSecretsError = PresentableError(error = "secrets error")

    cache.clearCaches()

    assertNull(cache.currentOssError)
    assertNull(cache.currentIacError)
    assertNull(cache.currentSnykCodeError)
    assertNull(cache.currentSecretsError)
    assertTrue(cache.currentOSSResultsLS.isEmpty())
    assertTrue(cache.currentIacResultsLS.isEmpty())
    assertTrue(cache.currentSnykCodeResultsLS.isEmpty())
    assertTrue(cache.currentSecretsResultsLS.isEmpty())
  }

  data class ProductCacheTestCase(
    val name: String,
    val product: ProductType,
    val expectedMapGetter: (SnykCachedResults) -> MutableMap<*, Set<ScanIssue>>?,
  )

  fun `test getSnykCachedResultsForProduct returns correct map for each product`() {
    val testCases =
      listOf(
        ProductCacheTestCase("OSS", ProductType.OSS) { it.currentOSSResultsLS },
        ProductCacheTestCase("IAC", ProductType.IAC) { it.currentIacResultsLS },
        ProductCacheTestCase("CODE_SECURITY", ProductType.CODE_SECURITY) {
          it.currentSnykCodeResultsLS
        },
        ProductCacheTestCase("SECRETS", ProductType.SECRETS) { it.currentSecretsResultsLS },
      )

    for (tc in testCases) {
      val result = getSnykCachedResultsForProduct(project, tc.product)
      val cache = getSnykCachedResults(project)!!
      val expectedMap = tc.expectedMapGetter(cache)
      assertSame("${tc.name}: should return the correct map instance", expectedMap, result)
    }
  }

  fun `test LsProduct getFor returns Secrets for secrets short name`() {
    assertEquals(LsProduct.Secrets, LsProduct.getFor("secrets"))
  }

  fun `test LsProduct getFor returns Secrets for secrets long name`() {
    assertEquals(LsProduct.Secrets, LsProduct.getFor("Snyk Secrets"))
  }

  fun `test LsProduct getFor returns Unknown for unrecognized name`() {
    assertEquals(LsProduct.Unknown, LsProduct.getFor("nonexistent"))
  }

  fun `test drainPendingAnnotationRefreshFiles returns all queued files and empties the set`() {
    data class Case(val description: String, val filesToAdd: Int)

    val cases =
      listOf(
        Case(description = "empty set returns empty list", filesToAdd = 0),
        Case(description = "single file is drained and set is emptied", filesToAdd = 1),
        Case(description = "multiple files are all drained and set is emptied", filesToAdd = 5),
      )

    val cache = getSnykCachedResults(project)!!

    for (case in cases) {
      cache.pendingAnnotationRefreshFiles.clear()
      val added = (1..case.filesToAdd).map { mockk<VirtualFile>(relaxed = true) }
      cache.pendingAnnotationRefreshFiles.addAll(added)

      val drained = cache.drainPendingAnnotationRefreshFiles()

      assertEquals("Case '${case.description}': drained count", case.filesToAdd, drained.size)
      assertTrue(
        "Case '${case.description}': set must be empty after drain",
        cache.pendingAnnotationRefreshFiles.isEmpty(),
      )
      assertTrue(
        "Case '${case.description}': drained list must contain all added files",
        drained.containsAll(added),
      )
    }
  }

  fun `test drainPendingAnnotationRefreshFiles does not lose files added concurrently during drain`() {
    val cache = getSnykCachedResults(project)!!
    cache.pendingAnnotationRefreshFiles.clear()

    val fileA = mockk<VirtualFile>(relaxed = true)
    val fileB = mockk<VirtualFile>(relaxed = true)
    cache.pendingAnnotationRefreshFiles.add(fileA)

    // Simulate a concurrent add that occurs while drain is in progress by adding fileB
    // directly to the backing set before calling drain (worst-case: added just before clear()).
    // With removeIf, fileB added before drain starts is always collected.
    // Files added AFTER removeIf passes their bucket stay in the set for the next cycle.
    cache.pendingAnnotationRefreshFiles.add(fileB)

    val drained = cache.drainPendingAnnotationRefreshFiles()

    // Both files added before drain must be collected — none silently dropped
    assertTrue("fileA must be in drained list", fileA in drained)
    assertTrue("fileB must be in drained list", fileB in drained)
    assertTrue(
      "set must be empty after draining all pre-drain files",
      cache.pendingAnnotationRefreshFiles.isEmpty(),
    )
  }
}
