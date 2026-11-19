package snyk

import com.intellij.testFramework.LightPlatform4TestCase
import org.junit.Test

class SnykBundleTest : LightPlatform4TestCase() {
  @Test
  fun `message resolves a bundle key`() {
    assertEquals(
      "Nested Folder Configuration Detected",
      SnykBundle.message("snyk.folderConfig.migration.title"),
    )
  }
}
