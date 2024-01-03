package io.snyk.plugin.ui.toolwindow

import com.intellij.ui.components.labels.LinkLabel
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.net.URL
import javax.swing.JLabel

class LabelProviderTest {
    @Before
    fun setUp() {
        unmockkAll()
        // test URL - unfortunately, LinkLabel does not provide an easy way to verify
        // the link action, so we're using a static mock of the create method to check the action
        mockkStatic(LinkLabel::class)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `getDependencyLabel should provide a label with a clickable link to package at npmBaseUrl for npm packages`() {
        val packageName = "packageName"

        val label: JLabel = LabelProvider().getDependencyLabel("npm", packageName)

        assertTrue("Expected LinkLabel, but got ${label::class}", label is LinkLabel<*>)
        assertEquals(packageName, label.text)
        verifyLinkLabelCreated(LabelProvider.npmBaseUrl + "/$packageName")
    }

    @Test
    fun `getDependencyLabel should provide a plain text label for non-npm packages`() {
        val packageName = "package"

        val output: JLabel = LabelProvider().getDependencyLabel("maven", packageName)

        assertEquals(JLabel::class, output::class)
        assertEquals(packageName, output.text)
    }

    @Test
    fun `getCWELabel should provide a label with a clickable link to the CWE info at cweBaseUrl`() {
        val cwe = "CWE-400"

        val output: JLabel = LabelProvider().getCWELabel(cwe)

        assertEquals(LinkLabel::class, output::class)
        assertEquals(cwe, output.text)
        verifyLinkLabelCreated(LabelProvider.CWE_BASE_URL + "/${cwe.removePrefix("CWE-")}.html")
    }

    @Test
    fun `getCVELabel should provide a label with a clickable link to the CVE info at cveBaseUrl`() {
        val cve = "CVE123"

        val output: JLabel = LabelProvider().getCVELabel(cve)

        assertEquals(LinkLabel::class, output::class)
        assertEquals(cve, output.text)
        verifyLinkLabelCreated(LabelProvider.CVE_BASE_URL + "=$cve")
    }

    @Test
    fun `getVulnerability should provide a label with a clickable link to the vulnerability at vulnerabilityBaseUrl`() {
        val id = "VULN123"

        val output: JLabel = LabelProvider().getVulnerabilityLabel(id)

        assertEquals(LinkLabel::class, output::class)
        assertEquals(id, output.text)
        verifyLinkLabelCreated(LabelProvider.VULNERABILITY_BASE_URL + "/$id")
    }

    @Test
    fun `getVulnerability should provide a label with a explicit link if provided`() {
        val id = "VULN123"
        val url = "https://some.link"

        val output: JLabel = LabelProvider().getVulnerabilityLabel(id, url)

        assertEquals(LinkLabel::class, output::class)
        assertEquals(id, output.text)
        verifyLinkLabelCreated(url)
    }

    @Test
    fun `getVulnerability should provide an uppercase label and keep vulnerability ID case sensitivity in url`() {
        val id = "vuln123"

        val output = LabelProvider().getVulnerabilityLabel(id)

        assertEquals(LinkLabel::class, output::class)
        assertEquals(id.toUpperCase(), output.text)
        verifyLinkLabelCreated(LabelProvider.VULNERABILITY_BASE_URL + "/$id")
    }

    @Test
    fun `getCVSSLabel should provide a label with a clickable link to the CVSS scoring calculator for this score`() {
        val cvssText = "package"
        val cvssId = "1"
        val output: JLabel = LabelProvider().getCVSSLabel(cvssText, cvssId)

        assertEquals(LinkLabel::class, output::class)
        assertEquals(cvssText, output.text)

        verifyLinkLabelCreated(LabelProvider.CVSS_BASE_URL + "#$cvssId")
    }

    @Test
    fun `getLinkLabel should provide a label with a clickable link`() {
        val url = "https://snyk.io/reference"
        val text = "reference"

        val label: JLabel = LabelProvider().createActionLink(URL(url), text)

        assertEquals(text, label.text)
        verifyLinkLabelCreated(url)
    }

    private fun verifyLinkLabelCreated(expectedUrl: String) {
        val actionSlot = slot<Runnable>()
        verify {
            LinkLabel.create(any(), capture(actionSlot))
        }
        val actualUrl = (actionSlot.captured as LabelProvider.OpenLinkAction).url.toExternalForm()
        assertEquals(expectedUrl, actualUrl)
    }
}
