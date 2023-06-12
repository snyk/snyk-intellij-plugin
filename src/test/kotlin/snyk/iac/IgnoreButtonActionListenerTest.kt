package snyk.iac

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import junit.framework.TestCase.assertEquals
import org.junit.After
import org.junit.Before
import org.junit.Test
import snyk.common.IgnoreService
import java.awt.event.ActionEvent
import javax.swing.JButton

class IgnoreButtonActionListenerTest {
    private val projectBasePath = ""
    private lateinit var issue: IacIssue
    private lateinit var project: Project
    private lateinit var service: IgnoreService
    private lateinit var button: JButton
    private lateinit var event: ActionEvent

    @Before
    fun setUp() {
        unmockkAll()
        event = mockk()
        service = mockk()
        project = mockk()
        every { project.basePath } returns projectBasePath
        button = JButton("test")
        issue = IacIssue(
            "issueId",
            "issueTitle",
            "issueSeverity",
            "issuePublicId",
            "issueDocumentation",
            1,
            "issueIssue",
            "issueImpact",
            "issueResolve"
        )
        button.isEnabled = true
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `actionPerformed should submit an IgnoreAction to be run async`() {
        mockkStatic(ProgressManager::class)
        val progressManager = mockk<ProgressManager>()
        val cut = IgnoreButtonActionListener(service, issue, null, project)
        val taskSlot = slot<IgnoreButtonActionListener.IgnoreTask>()

        every { ProgressManager.getInstance() } returns progressManager
        justRun { progressManager.run(capture(taskSlot)) }

        cut.actionPerformed(event)

        verify(exactly = 1) {
            progressManager.run(any<IgnoreButtonActionListener.IgnoreTask>())
        }

        assertEquals(taskSlot.captured.project, project)
        assertEquals(taskSlot.captured.e, event)
        assertEquals(taskSlot.captured.ignoreService, service)
        assertEquals(taskSlot.captured.issue, issue)
    }
}
