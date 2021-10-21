package snyk.iac

import junit.framework.TestCase.assertNotNull
import org.junit.Before
import org.junit.Test
import java.awt.Container
import javax.swing.JButton

class IacSuggestionDescriptionPanelTest {

    private lateinit var cut: IacSuggestionDescriptionPanel
    private lateinit var issue: IacIssue

    @Before
    fun setUp() {
        issue = IacIssue(
            "IacTestIssue",
            "TestTitle",
            "TestSeverity",
            "IacTestIssuePublicString",
            "Test Documentation",
            123,
            "TestIssue",
            "TestImpact",
            "TestResolve",
            listOf("Test Reference 1", "Test reference 2"),
            listOf("Test Path 1", "Test Path 2")
        )

        cut = IacSuggestionDescriptionPanel(issue)
    }

    @Test
    fun `IacSuggestionDescriptionPanel should have ignore button`() {
        val expectedButtonText = "Ignore This Issue"
        val actualButton = getJButtonByText(cut, expectedButtonText)
        assertNotNull("Didn't find button with text $expectedButtonText", actualButton)
    }

    private fun getJButtonByText(parent: Container, text: String): JButton? {
        val components = parent.components
        var found: JButton? = null
        for (component in components) {
            if (component is JButton && text == component.text) {
                found = component
            } else if (component is Container) {
                found = getJButtonByText(component, text)
            }
            if (found != null) {
                break
            }
        }
        return found
    }
}
