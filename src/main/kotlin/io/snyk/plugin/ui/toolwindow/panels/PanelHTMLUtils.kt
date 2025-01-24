package io.snyk.plugin.ui.toolwindow.panels

import com.jetbrains.rd.generator.nova.PredefinedType
import io.snyk.plugin.ui.jcef.ThemeBasedStylingGenerator

class PanelHTMLUtils {
    companion object {
        fun getNonce(): String {
            val allowedChars = ('A'..'Z') + ('a'..'z') + ('0'..'9')
            return (1..32)
                .map { allowedChars.random() }
                .joinToString("")
        }

        fun getFormattedHtml(html: String, ideScript: String = ""): String {
            val nonce = extractLsNonceIfPresent(html)?: getNonce()
            var formattedHtml = html.replace("\${ideStyle}", "<style nonce=\${nonce}></style>")
            formattedHtml = formattedHtml.replace("\${headerEnd}", "")
            formattedHtml = formattedHtml.replace(
                "\${ideScript}", "<script nonce=\${nonce}>$ideScript</script>")
            formattedHtml = formattedHtml.replace("\${nonce}", nonce)
            formattedHtml = ThemeBasedStylingGenerator.replaceWithCustomStyles(formattedHtml)
            return formattedHtml
        }

        private fun extractLsNonceIfPresent(html: String): String?{
            // When the nonce is injected by the IDE, it is of format nonce-${nonce}
            if (!html.contains("\${nonce}") && html.contains("nonce-")){
                val nonceStartPosition = html.indexOf("nonce-")
                // Length of LS nonce
                val startIndex = nonceStartPosition + "nonce-".length
                val endIndex = startIndex + 24
                return html.substring(startIndex, endIndex ).trim()
            }
            return null
        }
    }
}
