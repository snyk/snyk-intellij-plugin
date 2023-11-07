package snyk.common.lsp

import org.eclipse.lsp4j.MessageActionItem
import org.eclipse.lsp4j.MessageParams
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.ShowMessageRequestParams
import org.eclipse.lsp4j.services.LanguageClient
import java.util.concurrent.CompletableFuture

class SnykLanguageClient : LanguageClient {
    override fun telemetryEvent(`object`: Any?) {
        // do nothing
    }

    override fun publishDiagnostics(diagnostics: PublishDiagnosticsParams?) {
        // do nothing
    }

    override fun showMessage(messageParams: MessageParams?) {
        // do nothing
    }

    override fun showMessageRequest(requestParams: ShowMessageRequestParams?): CompletableFuture<MessageActionItem> {
        // do nothing
        TODO()
    }

    override fun logMessage(message: MessageParams?) {
        message?.let { println(message.message) }
    }
}
