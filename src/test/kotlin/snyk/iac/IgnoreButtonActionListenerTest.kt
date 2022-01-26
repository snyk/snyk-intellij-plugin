package snyk.iac

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.calcRelativeToProjectPath
import com.intellij.psi.PsiFile
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import snyk.common.IgnoreService
import java.awt.event.ActionEvent
import javax.swing.JButton

class IgnoreButtonActionListenerTest {
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

    @Test
    fun `task should use ignore service to ignore and then mark IacIssue as ignored and disable the button`() {
        val task = IgnoreButtonActionListener.IgnoreTask(project, service, issue, null, event)

        justRun { service.ignore(issue.id) }
        every { event.source } returns button

        mockkStatic(ApplicationManager::class)
        justRun { ApplicationManager.getApplication().invokeLater(any()) }

        task.run(mockk())

        verify(exactly = 1) { service.ignore(issue.id) }

        assertEquals(IgnoreButtonActionListener.IGNORED_ISSUE_BUTTON_TEXT, button.text)
        assertFalse("Expected button to be disabled after ignore action", button.isEnabled)
        assertTrue("Expected issue to be marked ignored after ignore action", issue.ignored)
    }

    @Test
    fun `task should use ignore service to ignore instance and mark issue as ignored and disable the button`() {
        val mockPsiFile = mockk<PsiFile>(relaxed = true)
        val task = IgnoreButtonActionListener.IgnoreTask(project, service, issue, mockPsiFile, event)

        justRun { service.ignoreInstance(issue.id, any()) }
        mockkStatic("com.intellij.openapi.project.ProjectUtil")
        val relativeFilePath = ".../test/test.yaml"
        every { calcRelativeToProjectPath(any(), any()) } returns relativeFilePath
        val sanitizedFilePath = relativeFilePath.replace(".../", "")
        val path = "$sanitizedFilePath > test > segment2"
        every { service.buildPath(any(), any()) } returns path
        every { event.source } returns button
        mockkStatic(ApplicationManager::class)
        justRun { ApplicationManager.getApplication().invokeLater(any()) }

        task.run(mockk())

        verify(exactly = 1) {
            service.buildPath(issue, sanitizedFilePath)
            service.ignoreInstance(issue.id, path)
        }

        assertEquals(IgnoreButtonActionListener.IGNORED_ISSUE_BUTTON_TEXT, button.text)
        assertFalse("Expected button to be disabled after ignore action", button.isEnabled)
        assertTrue("Expected issue to be marked ignored after ignore action", issue.ignored)
    }
}
