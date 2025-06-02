package snyk.common.lsp

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.util.application
import io.snyk.plugin.events.SnykCliDownloadListener
import io.snyk.plugin.ui.toolwindow.SnykPluginDisposable

@Service(Service.Level.PROJECT)
class LanguageServerRestartListener(val project: Project) : Disposable {
    private var disposed = false

    fun isDisposed() = disposed || project.isDisposed
    override fun dispose() {
        this.disposed = true
    }

    init {
        Disposer.register(SnykPluginDisposable.getInstance(project), this)
        application.messageBus.connect()
            .subscribe(SnykCliDownloadListener.CLI_DOWNLOAD_TOPIC, object : SnykCliDownloadListener {
                override fun cliDownloadFinished(succeed: Boolean) {
                    if (succeed && !disposed) {
                        LanguageServerWrapper.getInstance(project).restart()
                    }
                }
            })
    }
}
