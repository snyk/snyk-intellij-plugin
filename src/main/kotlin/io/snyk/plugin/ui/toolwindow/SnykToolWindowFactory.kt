package io.snyk.plugin.ui.toolwindow

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory

/**
 * IntelliJ ToolWindowFactory for Snyk plugin.
 */
class SnykToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val toolWindowPanel = SnykToolWindow(project)
        val contentManager = toolWindow.contentManager
        val content = contentManager.factory.createContent(toolWindowPanel, null, false)
        contentManager.addContent(content)

        Disposer.register(project, toolWindowPanel)
    }

    companion object {
        const val SNYK_TOOL_WINDOW = "Snyk"
    }
}
