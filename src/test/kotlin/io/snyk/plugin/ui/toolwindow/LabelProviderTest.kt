package io.snyk.plugin.ui.toolwindow

import com.intellij.ui.components.ActionLink
import junit.framework.TestCase.assertEquals
import org.junit.Test
import java.net.URL

class LabelProviderTest {

    @Test
    fun `getDependencyLabel should provide a label with a clickable link to package at npmBaseUrl for npm packages`() {
        val packageName = "packageName"

        val label: ActionLink = LabelProvider().getDependencyLabel("npm", packageName)

        assertEquals(packageName, label.text)
    }

    @Test
    fun `getDependencyLabel should provide a plain text label for non-npm packages`() {
        val packageName = "package"

        val output: ActionLink = LabelProvider().getDependencyLabel("maven", packageName)

        assertEquals(packageName, output.text)
    }

    @Test
    fun `getCWELabel should provide a label with a clickable link to the CWE info at cweBaseUrl`() {
        val cwe = "CWE-400"

        val output: ActionLink = LabelProvider().getCWELabel(cwe)

        assertEquals(cwe, output.text)
    }

    @Test
    fun `getCVELabel should provide a label with a clickable link to the CVE info at cveBaseUrl`() {
        val cve = "CVE123"

        val output: ActionLink = LabelProvider().getCVELabel(cve)

        assertEquals(cve, output.text)
    }

    @Test
    fun `getVulnerability should provide a label with a clickable link to the vulnerability at vulnerabilityBaseUrl`() {
        val id = "VULN123"

        val output: ActionLink = LabelProvider().getVulnerabilityLabel(id)

        assertEquals(id, output.text)
    }

    @Test
    fun `getVulnerability should provide a label with a explicit link if provided`() {
        val id = "VULN123"
        val url = "https://some.link"

        val output: ActionLink = LabelProvider().getVulnerabilityLabel(id, url)

        assertEquals(id, output.text)
    }

    @Test
    fun `getVulnerability should provide an uppercase label and keep vulnerability ID case sensitivity in url`() {
        val id = "vuln123"

        val output = LabelProvider().getVulnerabilityLabel(id)

        assertEquals(id.uppercase(), output.text)
    }

    @Test
    fun `getCVSSLabel should provide a label with a clickable link to the CVSS scoring calculator for this score`() {
        val cvssText = "package"
        val cvssId = "1"
        val output: ActionLink = LabelProvider().getCVSSLabel(cvssText, cvssId)

        assertEquals(cvssText, output.text)
    }

    @Test
    fun `getLinkLabel should provide a label with a clickable link`() {
        val url = "https://snyk.io/reference"
        val text = "reference"

        val label: ActionLink = LabelProvider().createActionLink(URL(url), text)

        assertEquals(text, label.text)
    }
}
