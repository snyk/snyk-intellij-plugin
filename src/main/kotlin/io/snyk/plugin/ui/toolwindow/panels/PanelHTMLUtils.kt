package io.snyk.plugin.ui.toolwindow.panels

import com.intellij.ui.jcef.JBCefScrollbarsHelper
import io.snyk.plugin.ui.jcef.JCEFUtils
import io.snyk.plugin.ui.jcef.JcefAvailability
import io.snyk.plugin.ui.jcef.ThemeBasedStylingGenerator

class PanelHTMLUtils {
  companion object {

    // Get any custom CSS. At the minimum, include the IDE's native scrollbar style.
    // Only ever invoked when the embedded browser is available: the body references a
    // com.intellij.ui.jcef type, so verifying it would raise a LinkageError on IDE builds where
    // JCEF is not reachable (2026.2+ without the bundled Web Browser plugin).
    private fun getCss() = JBCefScrollbarsHelper.buildScrollbarsStyle()

    private fun getCssIfAvailable() = if (JcefAvailability.isAvailable()) getCss() else ""

    // Must not have blank template replacements, as they could be used to skip the "${nonce}"
    // injection check
    // and still end up with the nonce injected, e.g. "${nonce${resolvesToEmpty}}" becomes
    // "${nonce}" - See IDE-1050.
    fun getFormattedHtml(html: String, ideScript: String = " "): String {
      val nonce = extractLsNonceIfPresent(html) ?: JCEFUtils.generateNonce()
      var formattedHtml =
        html.replace("\${ideStyle}", "<style nonce=\${nonce}>${getCssIfAvailable()}</style>")
      formattedHtml = formattedHtml.replace("\${headerEnd}", " ")
      formattedHtml = formattedHtml.replace("\${ideScript}", ideScript)
      formattedHtml = formattedHtml.replace("\${ideGenerateAIFix}", getGenerateAiFixScript())
      formattedHtml = formattedHtml.replace("\${ideApplyAIFix}", getApplyAiFixScript())
      formattedHtml =
        formattedHtml.replace("\${ideSubmitIgnoreRequest}", getSubmitIgnoreRequestScript())
      formattedHtml = formattedHtml.replace("\${nonce}", nonce)
      formattedHtml = ThemeBasedStylingGenerator.replaceWithCustomStyles(formattedHtml)
      return formattedHtml
    }

    private fun extractLsNonceIfPresent(html: String): String? {
      // When the nonce is injected by the IDE, it is of format nonce-${nonce}
      return if (!html.contains("\${nonce}") && html.contains("nonce-")) {
        val nonceStartPosition = html.indexOf("nonce-")
        // Length of LS nonce
        val startIndex = nonceStartPosition + "nonce-".length
        val endIndex = startIndex + 24
        html.substring(startIndex, endIndex).trim()
      } else {
        null
      }
    }

    private fun getGenerateAiFixScript(): String = "window.aiFixQuery(issueId);\n"

    private fun getApplyAiFixScript(): String = "window.applyFixQuery(fixId);\n"

    private fun getSubmitIgnoreRequestScript(): String =
      "window.submitIgnoreRequest(issueId + '@|@' + ignoreType + '@|@' + ignoreExpirationDate + '@|@' + ignoreReason);\n"
  }
}
