package snyk.iac

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import org.junit.Test
import java.awt.event.ActionEvent
import javax.swing.JButton

class IgnoreButtonActionListenerTest {
    @Test
    fun `actionPerformed should call ignore on IacIgnoreService with Issue`() {
        val service = mockk<IacIgnoreService>()
        val issue = mockk<IacIssue>()
        val cut = IgnoreButtonActionListener(service, issue)
        val event = mockk<ActionEvent>()
        val button = JButton("test")
        button.isEnabled = true

        every { service.ignore(any()) } returns ""
        every { event.source } returns button

        cut.actionPerformed(event)

        verify(exactly = 1) {
            service.ignore(issue)
        }

        assertEquals("Issue now ignored", button.text)
        assertFalse("Expected button to be disabled after ignore action", button.isEnabled)
    }
}
