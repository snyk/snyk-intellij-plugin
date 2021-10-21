package snyk.iac

import junit.framework.TestCase.assertEquals
import org.junit.After
import org.junit.Before
import org.junit.Test
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

    @After
    fun tearDown() {
    }

    @Test
    fun `panel should have ignore button`() {
        val actualButton: JButton
        assertEquals("Ignore This Issue", actualButton.text)
    }
}
