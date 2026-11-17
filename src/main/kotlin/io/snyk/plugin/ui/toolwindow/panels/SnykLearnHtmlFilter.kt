package io.snyk.plugin.ui.toolwindow.panels

internal object SnykLearnHtmlFilter {
  private val learnSection =
    Regex(
      """<div\b(?=[^>]*\bclass\s*=\s*(?:"[^"]*\bsn-learn\b[^"]*"|'[^']*\bsn-learn\b[^']*'))[^>]*>.*?</a\s*>\s*</div\s*>""",
      setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )

  private val learnLink =
    Regex(
      """<a\b(?=[^>]*\bhref\s*=\s*(?:"https?://(?:learn\.snyk\.io(?:/[^"]*)?|(?:www\.)?snyk\.io/learn(?:/[^"]*)?)"|'https?://(?:learn\.snyk\.io(?:/[^']*)?|(?:www\.)?snyk\.io/learn(?:/[^']*)?)'))[^>]*>.*?</a\s*>""",
      setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )

  fun removeLearnContent(html: String): String =
    learnLink.replace(learnSection.replace(html, ""), "")
}
