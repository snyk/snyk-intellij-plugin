package io.snyk.plugin

import com.intellij.openapi.application.ApplicationActivationListener
import com.intellij.openapi.wm.IdeFrame

/**
 * Listens for application activation events (IDE window gaining focus) and refreshes
 * the Snyk tool window UI to ensure any updates that occurred while the window was
 * not visible are properly displayed.
 */
class SnykApplicationActivationListener : ApplicationActivationListener {

    override fun applicationActivated(ideFrame: IdeFrame) {
        val project = ideFrame.project ?: return
        if (project.isDisposed) return

        // Only refresh if the Snyk tool window is visible
        val toolWindow = snykToolWindow(project) ?: return
        if (!toolWindow.isVisible) return

        val toolWindowPanel = getSnykToolWindowPanel(project) ?: return
        toolWindowPanel.refreshUI()
    }
}
