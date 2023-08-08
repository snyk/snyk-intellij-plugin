package snyk.errorHandler

import com.intellij.diagnostic.AbstractMessage
import com.intellij.diagnostic.PluginException
import com.intellij.openapi.diagnostic.Attachment
import com.intellij.openapi.diagnostic.ErrorReportSubmitter
import com.intellij.openapi.diagnostic.IdeaLoggingEvent
import com.intellij.openapi.diagnostic.SubmittedReportInfo
import com.intellij.util.Consumer
import java.awt.Component

class SnykErrorReportSubmitter : ErrorReportSubmitter() {
    override fun getReportActionText(): String {
        return "Report to Snyk"
    }

    override fun submit(
        events: Array<out IdeaLoggingEvent>,
        additionalInfo: String?,
        parentComponent: Component,
        consumer: Consumer<in SubmittedReportInfo>
    ): Boolean {
        for (event in events) {
            var throwable = event.throwable
            val description = additionalInfo ?: ""
            var attachments = listOf<Attachment>()

            if (event.data is AbstractMessage) {
                throwable = (event.data as AbstractMessage).throwable
                attachments = (event.data as AbstractMessage).includedAttachments
            }
            if (throwable is PluginException && throwable.cause != null) {
                // unwrap PluginManagerCore.createPluginException
                throwable = throwable.cause
            }
            SentryErrorReporter.submitErrorReport(
                throwable,
                consumer = consumer,
                description = description,
                attachments = attachments
            )
        }
        return true
    }
}
