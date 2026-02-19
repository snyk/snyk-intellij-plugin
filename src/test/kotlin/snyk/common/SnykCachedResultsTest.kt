package snyk.common

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.snyk.plugin.getSnykCachedResults
import io.snyk.plugin.getSnykCachedResultsForProduct
import io.snyk.plugin.resetSettings
import org.junit.Test
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

  @Test
  fun `test clearCaches clears secrets results and error`() {
    val cache = getSnykCachedResults(project)!!
    cache.currentSecretsError = PresentableError(error = "test error")
    cache.currentSecretsResultsLS[mockk(relaxed = true)] = emptySet()

    cache.clearCaches()

    assertNull(cache.currentSecretsError)
    assertTrue(cache.currentSecretsResultsLS.isEmpty())
  }

  @Test
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

  @Test
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

  @Test
  fun `test LsProduct getFor returns Secrets for secrets short name`() {
    assertEquals(LsProduct.Secrets, LsProduct.getFor("secrets"))
  }

  @Test
  fun `test LsProduct getFor returns Secrets for secrets long name`() {
    assertEquals(LsProduct.Secrets, LsProduct.getFor("Snyk Secrets"))
  }

  @Test
  fun `test LsProduct getFor returns Unknown for unrecognized name`() {
    assertEquals(LsProduct.Unknown, LsProduct.getFor("nonexistent"))
  }
}
