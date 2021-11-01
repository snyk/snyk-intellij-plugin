package io.snyk.plugin.services.download

import com.intellij.notification.NotificationAction
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.util.io.HttpRequests
import io.mockk.CapturingSlot
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import io.snyk.plugin.ui.SnykBalloonNotificationHelper
import junit.framework.TestCase.assertEquals
import org.junit.After
import org.junit.Before
import org.junit.Test

class CliDownloaderErrorHandlerTest {

    private lateinit var indicator: ProgressIndicator
    private lateinit var project: Project
    private lateinit var cut: CliDownloaderErrorHandler

    private lateinit var retryActionSlot: CapturingSlot<NotificationAction>
    private lateinit var contactActionSlot: CapturingSlot<NotificationAction>
    private lateinit var messageSlot: CapturingSlot<String>
    private lateinit var projectSlot: CapturingSlot<Project>

    @Before
    fun setUp() {
        clearAllMocks()
        unmockkAll()
        mockkObject(SnykBalloonNotificationHelper)

        project = mockk()
        indicator = mockk()

        contactActionSlot = slot()
        messageSlot = slot()
        projectSlot = slot()

        cut = CliDownloaderErrorHandler()
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun showErrorWithWithRetryAndContactAction_shouldShowErrorWithActions() {
        val notificationMessage = "Test message"

        retryActionSlot = slot()

        every {
            SnykBalloonNotificationHelper.showError(
                capture(messageSlot),
                capture(projectSlot),
                capture(retryActionSlot),
                capture(contactActionSlot)
            )
        } returns mockk()

        cut.showErrorWithRetryAndContactAction(notificationMessage, mockk(), project)

        assertEquals("Retry CLI download", retryActionSlot.captured.templateText)
        assertEquals("Contact support...", contactActionSlot.captured.templateText)
        assertEquals(notificationMessage, messageSlot.captured)
        assertEquals(project, projectSlot.captured)

        verify(exactly = 1) {
            SnykBalloonNotificationHelper.showError(any(), any(), any(), any())
        }
    }

    @Test
    fun handleHttpStatusException_shouldDisplayErrorMessage() {
        val exception = HttpRequests.HttpStatusException("Forbidden", 403, "url")

        every {
            SnykBalloonNotificationHelper.showError(
                capture(messageSlot),
                capture(projectSlot),
                capture(contactActionSlot)
            )
        } returns mockk()

        cut.handleHttpStatusException(exception, project)

        assertEquals(cut.getHttpStatusErrorNotificationMessage(exception), messageSlot.captured)
        assertEquals(project, projectSlot.captured)
        assertEquals("Contact support...", contactActionSlot.captured.templateText)

        verify(exactly = 1) {
            SnykBalloonNotificationHelper.showError(any(), any(), any())
        }
    }
}
