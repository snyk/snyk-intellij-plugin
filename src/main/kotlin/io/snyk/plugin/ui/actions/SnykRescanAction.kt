package io.snyk.plugin.ui.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware

/**
 * Re-scan project with Snyk action.
 */
class SnykRescanAction : AnAction(AllIcons.Actions.Refresh), DumbAware {

    override fun actionPerformed(actionEvent: AnActionEvent) {
        TODO("Not yet implemented")
    }
}
