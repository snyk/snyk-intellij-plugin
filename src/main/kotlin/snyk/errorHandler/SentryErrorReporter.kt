package snyk.errorHandler

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Attachment
import com.intellij.openapi.diagnostic.SubmittedReportInfo
import com.intellij.openapi.diagnostic.SubmittedReportInfo.SubmissionStatus
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.Consumer
import io.sentry.DuplicateEventDetectionEventProcessor
import io.sentry.Sentry
import io.sentry.SentryEvent
import io.sentry.SentryLevel
import io.sentry.protocol.Message
import io.sentry.protocol.OperatingSystem
import io.sentry.protocol.SentryId
import io.sentry.protocol.SentryRuntime
import io.snyk.plugin.pluginSettings
import snyk.PropertyLoader
import snyk.common.isAnalyticsPermitted
import snyk.pluginInfo

object SentryErrorReporter {
    private val LOG = logger<SentryErrorReporter>()
    private const val MAX_ERROR_MESSAGE_LENGTH = 2000

    init {
        Sentry.init { options ->
            options.dsn = PropertyLoader.sentryDsn
            options.environment = PropertyLoader.environment
            options.release = pluginInfo.integrationVersion
            options.eventProcessors.removeIf { eventProcessor ->
                // users might have written different error messages that might then be discarded
                eventProcessor is DuplicateEventDetectionEventProcessor
            }

            options.setBeforeSend { event, _ ->
                val os = OperatingSystem()
                os.name = SystemInfo.OS_NAME
                os.version = "${SystemInfo.OS_VERSION}-${SystemInfo.OS_ARCH}"
                event.contexts.setOperatingSystem(os)

                val runtime = SentryRuntime()
                runtime.name = pluginInfo.integrationEnvironment
                runtime.version = pluginInfo.integrationEnvironmentVersion
                event.contexts.setRuntime(runtime)

                event.setTag("java.vendor", SystemInfo.JAVA_VENDOR)
                event.setTag("java.version", SystemInfo.JAVA_VERSION)
                event.setTag("java.runtime.version", SystemInfo.JAVA_RUNTIME_VERSION)

                // clear the server name
                event.serverName = null

                event
            }
        }
    }

    fun submitErrorReport(
        error: Throwable,
        consumer: Consumer<in SubmittedReportInfo>,
        attachments: List<Attachment> = emptyList(),
        description: String = ""
    ) {
        val event = SentryEvent()

        val message = Message()
        if (!StringUtil.isEmptyOrSpaces(description)) {
            message.message = description
            event.setTag("with.description", "true")
        }

        event.message = message
        event.level = SentryLevel.ERROR
        event.throwable = error

        Sentry.withScope { scope ->
            for (attachment in attachments) {
                val fileAttachment = io.sentry.Attachment(attachment.bytes, attachment.path)
                scope.addAttachment(fileAttachment)
            }

            val errorMessage = error.message
            if (errorMessage != null && errorMessage.length > MAX_ERROR_MESSAGE_LENGTH) {
                val fileAttachment = io.sentry.Attachment(errorMessage.toByteArray(Charsets.UTF_8), "errorMessage.txt")
                scope.addAttachment(fileAttachment)
            }

            val sentryId = Sentry.captureEvent(event)

            if (sentryId != SentryId.EMPTY_ID) {
                LOG.info("Sentry event reported: $sentryId")
                consumer.consume(SubmittedReportInfo(SubmissionStatus.NEW_ISSUE))
            } else {
                LOG.warn("Unable to submit Sentry error information")
                consumer.consume(SubmittedReportInfo(SubmissionStatus.FAILED))
            }
        }
    }

    /**
     * Captures exception with Sentry. This method should be used in `try-catch` blocks.
     *
     * Note: [io.snyk.plugin.services.SnykApplicationSettingsStateService.crashReportingEnabled] is respected
     */
    fun captureException(throwable: Throwable): SentryId {
        if (ApplicationManager.getApplication().isUnitTestMode) return SentryId.EMPTY_ID

        val settings = pluginSettings()
        return if (settings.crashReportingEnabled && isAnalyticsPermitted()) {
            val sentryId = Sentry.captureException(throwable)
            LOG.info("Sentry event reported: $sentryId")
            sentryId
        } else {
            LOG.info("Sentry crash reporting is disabled")
            SentryId.EMPTY_ID
        }
    }
}
