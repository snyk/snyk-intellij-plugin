package snyk.common.lsp

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.util.Disposer
import io.snyk.plugin.events.SnykCliDownloadListener
import io.snyk.plugin.ui.toolwindow.SnykPluginDisposable

@Service(Service.Level.APP)
class LanguageServerRestartListener : Disposable {
    private var disposed = false

    fun isDisposed() = disposed
    override fun dispose() {
        this.disposed = true
    }

    companion object {
        @JvmStatic
        fun getInstance(): LanguageServerRestartListener = service()
    }

    init {
        Disposer.register(SnykPluginDisposable.getInstance(), this)
        ApplicationManager.getApplication().messageBus.connect()
            .subscribe(SnykCliDownloadListener.CLI_DOWNLOAD_TOPIC, object : SnykCliDownloadListener {
                override fun cliDownloadFinished(succeed: Boolean) {
                    if (succeed && !disposed) {
                        LanguageServerWrapper.getInstance().restart()
                    }
                }
            })
    }
}
