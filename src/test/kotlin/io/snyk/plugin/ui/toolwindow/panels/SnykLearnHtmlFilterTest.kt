package io.snyk.plugin.ui.toolwindow.panels

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SnykLearnHtmlFilterTest {
  @Test
  fun `removes Snyk Learn section from code issue details`() {
    val html =
      """
      <section class="sn-issue-header">
        <h1>SQL injection</h1>
        <div class="sn-learn">
          <div class="lesson-icon"><svg><path /></svg></div>
          <a class="lesson-link" href="https://learn.snyk.io/lesson/sql-injection/?loc=ide">
            Learn about this issue type
          </a>
        </div>
      </section>
      <main><a href="https://docs.snyk.io/fix">Fix guidance</a></main>
      """
        .trimIndent()

    val filtered = SnykLearnHtmlFilter.removeLearnContent(html)

    assertFalse(filtered.contains("sn-learn"))
    assertFalse(filtered.contains("learn.snyk.io"))
    assertFalse(filtered.contains("Learn about this issue type"))
    assertTrue(filtered.contains("<h1>SQL injection</h1>"))
    assertTrue(filtered.contains("""href="https://docs.snyk.io/fix""""))
  }

  @Test
  fun `removes Snyk Learn section from open source issue details`() {
    val html =
      """
      <div class="sn-learn show" id="learn">
        <svg><path /></svg>
        <a class="learn--link lesson-link" href="https://learn.snyk.io/lesson/prototype-pollution/">
          Learn about this issue type
        </a>
      </div>
      <section class="summary">Vulnerable module: lodash</section>
      """
        .trimIndent()

    val filtered = SnykLearnHtmlFilter.removeLearnContent(html)

    assertEquals(
      """<section class="summary">Vulnerable module: lodash</section>""",
      filtered.trim(),
    )
  }

  @Test
  fun `removes standalone Learn links without changing surrounding details`() {
    val html =
      """
      <p>
        See <a data-source="lesson" href='https://learn.snyk.io/lesson/xss/'>Snyk Learn</a>
        or <a href="https://owasp.org/www-community/attacks/xss/">OWASP guidance</a>.
      </p>
      """
        .trimIndent()

    val filtered = SnykLearnHtmlFilter.removeLearnContent(html)

    assertFalse(filtered.contains("Snyk Learn"))
    assertFalse(filtered.contains("learn.snyk.io"))
    assertTrue(filtered.contains("See "))
    assertTrue(filtered.contains("or "))
    assertTrue(filtered.contains("""href="https://owasp.org/www-community/attacks/xss/""""))
    assertTrue(filtered.contains("OWASP guidance"))
  }

  @Test
  fun `leaves unrelated HTML unchanged`() {
    val html =
      """<p>Learn how to remediate this issue in <a href="https://docs.snyk.io/">the documentation</a>.</p>"""

    assertEquals(html, SnykLearnHtmlFilter.removeLearnContent(html))
  }
}
