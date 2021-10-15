package io.snyk.plugin.ui.toolwindow

import com.intellij.ide.BrowserUtil
import com.intellij.ui.components.labels.LinkLabel
import java.net.URL
import javax.swing.JLabel

class LabelProvider {

    companion object {
        const val npmBaseUrl = "https://app.snyk.io/test/npm"
        const val cweBaseUrl = "https://cwe.mitre.org/data/definitions"
        const val vulnerabilityBaseUrl = "https://snyk.io/vuln"
        const val cvssBaseUrl = "https://www.first.org/cvss/calculator/3.1"
        const val cveBaseUrl = "https://cve.mitre.org/cgi-bin/cvename.cgi?name"
    }

    class OpenLinkAction(val url: URL) : Runnable {
        override fun run() {
            BrowserUtil.open(url.toExternalForm())
        }
    }

    fun getDependencyLabel(packageManager: String, packageName: String): JLabel {
        return if (packageManager != "npm") {
            JLabel(packageName)
        } else {
            val url = URL("$npmBaseUrl/$packageName")
            return createLinkLabel(url, packageName)
        }
    }

    fun getCWELabel(cwe: String): LinkLabel<*> {
        return createLinkLabel(URL("$cweBaseUrl/${cwe.removePrefix("CWE-")}.html"), cwe)
    }

    fun getVulnerabilityLabel(id: String): JLabel {

        return createLinkLabel(URL("$vulnerabilityBaseUrl/$id"), id)
    }

    fun getCVSSLabel(text: String, id: String): JLabel {
        return createLinkLabel(URL("$cvssBaseUrl#$id"), text)
    }

    fun getCVELabel(cve: String): JLabel {
        return createLinkLabel(URL("$cveBaseUrl=$cve"), cve)
    }

    private fun createLinkLabel(url: URL, text: String): LinkLabel<*> {
        val openLinkAction = OpenLinkAction(url)
        return LinkLabel.create(text, openLinkAction)
    }
}
