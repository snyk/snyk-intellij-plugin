package io.snyk.plugin

import com.intellij.openapi.application.ApplicationActivationListener
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.wm.IdeFrame

/**
 * Listens for application activation events (IDE window gaining focus) and refreshes
 * the Snyk tool window UI to ensure any updates that occurred while the window was
 * not visible are properly displayed.
 */
class SnykApplicationActivationListener : ApplicationActivationListener {
    private val logger = logger<SnykApplicationActivationListener>()

    override fun applicationActivated(ideFrame: IdeFrame) {
        logger.debug("Application activated, ideFrame=$ideFrame")
        val project = ideFrame.project ?: run {
            logger.debug("No project for ideFrame, skipping")
            return
        }
        if (project.isDisposed) {
            logger.debug("Project is disposed, skipping")
            return
        }

        // Only refresh if the Snyk tool window is visible
        val toolWindow = snykToolWindow(project) ?: run {
            logger.debug("Snyk tool window not found, skipping")
            return
        }
        if (!toolWindow.isVisible) {
            logger.debug("Snyk tool window not visible, skipping")
            return
        }

        val toolWindowPanel = getSnykToolWindowPanel(project) ?: run {
            logger.debug("Snyk tool window panel not found, skipping")
            return
        }
        logger.debug("Calling refreshUI on tool window panel")
        toolWindowPanel.refreshUI()
        logger.debug("refreshUI completed")
    }
}
