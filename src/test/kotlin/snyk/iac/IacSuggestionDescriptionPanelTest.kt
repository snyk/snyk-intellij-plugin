package snyk.iac

import com.intellij.openapi.project.Project
import io.mockk.every
import io.mockk.mockk
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import org.junit.Before
import org.junit.Test
import snyk.UIComponentFinder
import snyk.UIComponentFinder.getJButtonByText

class IacSuggestionDescriptionPanelTest {

    private lateinit var project: Project
    private lateinit var cut: IacSuggestionDescriptionPanel
    private lateinit var issue: IacIssue

    @Before
    fun setUp() {
        issue = IacIssue(
            "IacTestIssue",
            "TestTitle",
            "TestSeverity",
            "IacTestIssuePublicString",
            "https://TestDocumentation",
            123,
            "TestIssue",
            "TestImpact",
            "TestResolve",
            listOf("https://TestReference1", "https://TestReference2"),
            listOf("Test Path 1", "Test Path 2")
        )
        project = mockk()
        every { project.basePath } returns ""
    }

    @Test
    fun `IacSuggestionDescriptionPanel should have ignore button`() {
        val expectedButtonText = "Ignore This Issue"

        cut = IacSuggestionDescriptionPanel(issue, null, project)

        val actualButton = getJButtonByText(cut, expectedButtonText)
        assertNotNull("Didn't find button with text $expectedButtonText", actualButton)
    }

    @Test
    fun `IacSuggestionDescriptionPanel ignore button should call IacIgnoreService on click`() {
        val expectedButtonText = "Ignore This Issue"

        cut = IacSuggestionDescriptionPanel(issue, null, project)

        val actualButton = getJButtonByText(cut, expectedButtonText)
        assertNotNull("Didn't find Ignore Button", actualButton)
        val listener = actualButton!!.actionListeners.first() as IgnoreButtonActionListener
        assertEquals(IgnoreButtonActionListener::class, listener::class)
        assertEquals(issue.id, listener.issue.id)
    }

    @Test
    fun `IacSuggestionDescriptionPanel should surface references`() {
        cut = IacSuggestionDescriptionPanel(issue, null, project)

        issue.references.stream().forEach {
            val label = UIComponentFinder.getJLabelByText(cut, it)
            assertNotNull("Didn't find reference $it", label)
        }
    }
}
