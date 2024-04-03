package io.snyk.plugin.ui.toolwindow

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.NotNull


/**
 * Top-Level disposable for the Snyk plugin.
 */
@Service(Service.Level.APP, Service.Level.PROJECT)
class SnykPluginDisposable : Disposable {
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

    override fun dispose() = Unit
}
