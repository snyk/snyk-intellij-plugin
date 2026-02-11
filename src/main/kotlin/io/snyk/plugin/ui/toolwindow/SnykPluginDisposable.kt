package io.snyk.plugin.ui.toolwindow

import com.intellij.ide.AppLifecycleListener
import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.NotNull
import snyk.common.lsp.LanguageServerWrapper

/** Top-Level disposable for the Snyk plugin. */
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
    fun getInstance(): SnykPluginDisposable =
      ApplicationManager.getApplication().getService(SnykPluginDisposable::class.java)

    @NotNull
    fun getInstance(@NotNull project: Project): SnykPluginDisposable =
      project.getService(SnykPluginDisposable::class.java)
  }

  init {
    ApplicationManager.getApplication()
      .messageBus
      .connect(this)
      .subscribe(AppLifecycleListener.TOPIC, this)
  }

  override fun appClosing() {
    shutdownAllLanguageServers()
  }

  private fun shutdownAllLanguageServers() {
    ProjectUtil.getOpenProjects().forEach { project ->
      try {
        LanguageServerWrapper.getInstance(project).shutdown()
      } catch (_: Exception) {
        // do nothing
      }
    }
  }

  override fun appWillBeClosed(isRestart: Boolean) {
    shutdownAllLanguageServers()
  }
}
