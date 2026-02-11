package io.snyk.plugin.ui.toolwindow

import com.intellij.ide.BrowserUtil
import com.intellij.ui.components.ActionLink
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.net.URI
import java.net.URL

class LabelProvider {

  companion object {
    const val NPM_BASE_URL = "https://app.snyk.io/test/npm"
    const val CWE_BASE_URL = "https://cwe.mitre.org/data/definitions"
    const val VULNERABILITY_BASE_URL = "https://snyk.io/vuln"
    const val CVSS_BASE_URL = "https://www.first.org/cvss/calculator/3.1"
    const val CVE_BASE_URL = "https://cve.mitre.org/cgi-bin/cvename.cgi?name"
  }

  class OpenLinkAction(val url: URL) : ActionListener {
    override fun actionPerformed(e: ActionEvent?) {
      BrowserUtil.open(url.toExternalForm())
    }
  }

  fun getDependencyLabel(packageManager: String, packageName: String): ActionLink {
    return if (packageManager != "npm") {
      ActionLink(packageName)
    } else {
      val url = URI("$NPM_BASE_URL/$packageName").toURL()
      return createActionLink(url, packageName)
    }
  }

  fun getCWELabel(cwe: String): ActionLink {
    return createActionLink(URI("$CWE_BASE_URL/${cwe.removePrefix("CWE-")}.html").toURL(), cwe)
  }

  fun getVulnerabilityLabel(id: String, idUrl: String? = null): ActionLink {
    val url = idUrl ?: "$VULNERABILITY_BASE_URL/$id"
    return createActionLink(URI(url).toURL(), id.uppercase())
  }

  fun getCVSSLabel(text: String, id: String): ActionLink {
    return createActionLink(URI("$CVSS_BASE_URL#$id").toURL(), text)
  }

  fun getCVELabel(cve: String): ActionLink {
    return createActionLink(URI("$CVE_BASE_URL=$cve").toURL(), cve)
  }

  fun createActionLink(
    url: URL,
    text: String,
    customToolTipText: String = "Click to open description in the Browser",
  ): ActionLink {
    val openLinkAction = OpenLinkAction(url)
    return ActionLink(text, openLinkAction).apply { toolTipText = customToolTipText }
  }
}
