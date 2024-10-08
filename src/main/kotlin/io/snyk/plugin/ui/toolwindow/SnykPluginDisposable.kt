package io.snyk.plugin.ui.toolwindow

import com.intellij.ide.AppLifecycleListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.NotNull
import snyk.common.lsp.LanguageServerWrapper
import java.util.concurrent.TimeUnit


/**
 * Top-Level disposable for the Snyk plugin.
 */
@Service(Service.Level.APP, Service.Level.PROJECT)
class SnykPluginDisposable : Disposable, AppLifecycleListener {
    private var disposed = false
        get() {
            return ApplicationManager.getApplication().isDisposed || field
        }

    fun isDisposed() = disposed

    override fun dispose() {
        disposed = true
    }

    companion object {
        @NotNull
        fun getInstance(): SnykPluginDisposable {
            return ApplicationManager.getApplication().getService(SnykPluginDisposable::class.java)
        }

        @NotNull
        fun getInstance(@NotNull project: Project): SnykPluginDisposable {
            return project.getService(SnykPluginDisposable::class.java)
        }
    }

    init {
        ApplicationManager.getApplication().messageBus.connect().subscribe(AppLifecycleListener.TOPIC, this)
    }

    override fun appClosing() {
        try {
            LanguageServerWrapper.getInstance().shutdown()
        } catch (ignored: Exception) {
            // do nothing
        }
    }

    override fun appWillBeClosed(isRestart: Boolean) {
        try {
            LanguageServerWrapper.getInstance().shutdown()
        } catch (ignored: Exception) {
            // do nothing
        }
    }
}
