package snyk.common.annotator

import org.eclipse.lsp4j.CodeAction
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CodeActionIntentionTest {
  @Test
  fun `identifies Snyk Learn code actions`() {
    assertTrue(CodeAction("Learn more about SQL injection (Snyk)").isSnykLearnAction())
    assertTrue(CodeAction("Learn about prototype pollution (Snyk)").isSnykLearnAction())
  }

  @Test
  fun `does not identify unrelated code actions`() {
    assertFalse(CodeAction("Upgrade to lodash 4.17.21").isSnykLearnAction())
    assertFalse(CodeAction("Fix this issue with Snyk Agent Fix").isSnykLearnAction())
  }
}
