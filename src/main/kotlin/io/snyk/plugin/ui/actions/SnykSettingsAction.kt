package io.snyk.plugin.ui.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.DumbAware
import io.snyk.plugin.settings.SnykProjectSettingsConfigurable
import org.jetbrains.concurrency.runAsync

/**
 * Show Snyk settings panel action.
 */
class SnykSettingsAction : AnAction(AllIcons.General.Settings), DumbAware {

    override fun actionPerformed(actionEvent: AnActionEvent) {
        runAsync {
            if (actionEvent.project?.isDisposed == true) return@runAsync
            ShowSettingsUtil.getInstance()
                .showSettingsDialog(actionEvent.project!!, SnykProjectSettingsConfigurable::class.java)
        }
    }

    override fun update(actionEvent: AnActionEvent) {
        actionEvent.presentation.isEnabled = actionEvent.project != null && !actionEvent.project!!.isDisposed
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
}
