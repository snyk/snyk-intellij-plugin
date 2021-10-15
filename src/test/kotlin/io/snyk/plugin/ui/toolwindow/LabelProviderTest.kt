package io.snyk.plugin.ui.toolwindow

import com.intellij.ui.components.labels.LinkLabel
import io.mockk.clearAllMocks
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.verify
import org.junit.Before
import org.junit.Test
import javax.swing.JLabel
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LabelProviderTest {
    @Before
    fun setUp() {
        clearAllMocks()
        // test URL - unfortunately, LinkLabel does not provide an easy way to verify
        // the link action, so we're using a static mock of the create method to check the action
        mockkStatic(LinkLabel::class)
    }

    @Test
    fun shouldProvideLinkLabelWhenNpm() {
        val packageName = "packageName"

        val label: JLabel = LabelProvider().getDependencyLabel("npm", packageName)

        assertTrue(label is LinkLabel<*>, "Expected LinkLabel, but got ${label::class}")
        assertEquals(packageName, label.text)
        verifyLinkLabelCreated(LabelProvider.npmBaseUrl + "/$packageName")
    }

    @Test
    fun shouldProvidePlainTextLabelWhenNotNpm() {
        val packageName = "package"

        val output: JLabel = LabelProvider().getDependencyLabel("maven", packageName)

        assertEquals(JLabel::class, output::class)
        assertEquals(packageName, output.text)
    }

    @Test
    fun shouldProvideLinkLabelForCWE() {
        val cwe = "CWE-400"

        val output: JLabel = LabelProvider().getCWELabel(cwe)

        assertEquals(LinkLabel::class, output::class)
        assertEquals(cwe, output.text)
        verifyLinkLabelCreated(LabelProvider.cweBaseUrl + "/${cwe.removePrefix("CWE-")}.html")
    }

    @Test
    fun shouldProvideLinkLabelForCVE() {
        val cve = "CVE123"

        val output: JLabel = LabelProvider().getCVELabel(cve)

        assertEquals(LinkLabel::class, output::class)
        assertEquals(cve, output.text)
        verifyLinkLabelCreated(LabelProvider.cveBaseUrl + "=$cve")
    }

    @Test
    fun shouldProvideLinkLabelForVulnerability() {
        val id = "VULN123"

        val output: JLabel = LabelProvider().getVulnerabilityLabel(id)

        assertEquals(LinkLabel::class, output::class)
        assertEquals(id, output.text)
        verifyLinkLabelCreated(LabelProvider.vulnerabilityBaseUrl + "/$id")
    }

    @Test
    fun shouldProvideLinkLabelForCVSS() {
        val cvssText = "package"
        val cvssId = "1"
        val output: JLabel = LabelProvider().getCVSSLabel(cvssText, cvssId)

        assertEquals(LinkLabel::class, output::class)
        assertEquals(cvssText, output.text)

        verifyLinkLabelCreated(LabelProvider.cvssBaseUrl + "#$cvssId")
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
