package io.snyk.plugin.ui.toolwindow.panels

import com.intellij.ui.jcef.JBCefScrollbarsHelper
import io.snyk.plugin.ui.jcef.ThemeBasedStylingGenerator

class PanelHTMLUtils {
    companion object {

        // Get any custom CSS. At the minimum, include the IDE's native scrollbar style.
        private fun getCss() = JBCefScrollbarsHelper.buildScrollbarsStyle()

        private fun getNonce(): String {
            val allowedChars = ('A'..'Z') + ('a'..'z') + ('0'..'9')
            return (1..32)
                .map { allowedChars.random() }
                .joinToString("")
        }

        fun getFormattedHtml(html: String, ideScript: String = ""): String {
            val nonce = extractLsNonceIfPresent(html)?: getNonce()
            var formattedHtml = html.replace("\${ideStyle}", "<style nonce=\${nonce}>${getCss()}</style>")
            formattedHtml = formattedHtml.replace("\${headerEnd}", "")
            formattedHtml = formattedHtml.replace(
                "\${ideScript}", "$ideScript")
            formattedHtml = formattedHtml.replace("\${ideGenerateAIFix}", getGenerateAiFixScript())
            formattedHtml = formattedHtml.replace("\${ideApplyAIFix}", getApplyAiFixScript())
            formattedHtml = formattedHtml.replace("\${nonce}", nonce)
            formattedHtml = ThemeBasedStylingGenerator.replaceWithCustomStyles(formattedHtml)
            return formattedHtml
        }

        private fun extractLsNonceIfPresent(html: String): String?{
            // When the nonce is injected by the IDE, it is of format nonce-${nonce}
            return if (!html.contains("\${nonce}") && html.contains("nonce-")) {
                val nonceStartPosition = html.indexOf("nonce-")
                // Length of LS nonce
                val startIndex = nonceStartPosition + "nonce-".length
                val endIndex = startIndex + 24
                return html.substring(startIndex, endIndex ).trim()
            } else null
        }
        private fun getGenerateAiFixScript(): String {
            return "const issueId = generateAIFixButton.getAttribute('issue-id');\n" +
                "                        const folderPath = generateAIFixButton.getAttribute('folder-path');\n" +
                "                        const filePath = generateAIFixButton.getAttribute('file-path');\n" +
                "\n" +
                "                        window.aiFixQuery(folderPath + \"@|@\" + filePath + \"@|@\" + issueId);\n" +
                "                        "
        }
        private fun getApplyAiFixScript(): String {
            return "window.applyFixQuery(fixId + '|@' + filePath + '|@' + patch);\n"
        }

    }
}
