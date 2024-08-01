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
    companion object {
        @NotNull
        fun getInstance(): Disposable {
            return ApplicationManager.getApplication().getService(SnykPluginDisposable::class.java)
        }

        @NotNull
        fun getInstance(@NotNull project: Project): Disposable {
            return project.getService(SnykPluginDisposable::class.java)
        }
    }

    init {
        ApplicationManager.getApplication().messageBus.connect().subscribe(AppLifecycleListener.TOPIC, this)
    }

    override fun appClosing() {
        LanguageServerWrapper.getInstance().shutdown().get(2, TimeUnit.SECONDS)
    }

    override fun appWillBeClosed(isRestart: Boolean) {
        LanguageServerWrapper.getInstance().shutdown().get(2, TimeUnit.SECONDS)
    }

    override fun dispose() = Unit

}
