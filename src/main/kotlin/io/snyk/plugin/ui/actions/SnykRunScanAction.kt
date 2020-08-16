package io.snyk.plugin.ui.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAware
import io.snyk.plugin.cli.CliResult
import io.snyk.plugin.getCli
import io.snyk.plugin.ui.SnykToolWindowPanel

/**
 * Re-scan project with Snyk action.
 */
class SnykRunScanAction : AnAction(AllIcons.Toolwindows.ToolWindowRun), DumbAware {

    override fun actionPerformed(actionEvent: AnActionEvent) {
        val project = actionEvent.project!!

        ProgressManager.getInstance().run(object: Task.Backgroundable(project, "Snyk scanning", true) {
            override fun run(indicator: ProgressIndicator) {
                project.service<SnykToolWindowPanel>().clean()

                val cliResult: CliResult = getCli(project).scan()

                project.service<SnykToolWindowPanel>().vulnerabilities(cliResult.vulnerabilities.toList())
            }
        })
    }
}
