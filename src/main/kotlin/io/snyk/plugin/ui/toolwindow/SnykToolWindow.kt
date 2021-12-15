package io.snyk.plugin.ui.toolwindow

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import io.snyk.plugin.events.SnykScanListener
import io.snyk.plugin.events.SnykTaskQueueListener
import io.snyk.plugin.getSnykToolWindowPanel
import io.snyk.plugin.snykcode.SnykCodeResults
import snyk.common.SnykError
import snyk.container.ContainerResult
import snyk.iac.IacResult
import snyk.oss.OssResult

/**
 * IntelliJ ToolWindow for Snyk plugin.
 */
class SnykToolWindow(private val project: Project) : SimpleToolWindowPanel(false, true), Disposable {

    private val actionToolbar: ActionToolbar

    init {
        val actionManager = ActionManager.getInstance()
        val actionGroup = actionManager.getAction("io.snyk.plugin.ActionBar") as ActionGroup
        actionToolbar = actionManager.createActionToolbar("Snyk Toolbar", actionGroup, false)
        initialiseToolbar()
        toolbar = actionToolbar.component

        getSnykToolWindowPanel(project)?.let { setContent(it) }
    }

    private fun initialiseToolbar() {
        // update actions presentation immediately after running state changes (avoid default 500 ms delay)
        project.messageBus.connect(this)
            .subscribe(SnykScanListener.SNYK_SCAN_TOPIC, object : SnykScanListener {

                override fun scanningStarted() = updateActionsPresentation()

                override fun scanningOssFinished(ossResult: OssResult) = updateActionsPresentation()

                override fun scanningSnykCodeFinished(snykCodeResults: SnykCodeResults?) = updateActionsPresentation()

                override fun scanningIacFinished(iacResult: IacResult) = updateActionsPresentation()

                override fun scanningOssError(snykError: SnykError) = updateActionsPresentation()

                override fun scanningIacError(snykError: SnykError) = updateActionsPresentation()

                override fun scanningSnykCodeError(snykError: SnykError) = updateActionsPresentation()

                override fun scanningContainerFinished(containerResult: ContainerResult) = updateActionsPresentation()

                override fun scanningContainerError(snykError: SnykError) = updateActionsPresentation()
            })

        project.messageBus.connect(this)
            .subscribe(SnykTaskQueueListener.TASK_QUEUE_TOPIC, object : SnykTaskQueueListener {
                override fun stopped(wasOssRunning: Boolean, wasSnykCodeRunning: Boolean, wasIacRunning: Boolean) =
                    updateActionsPresentation()
            })
    }

    private fun updateActionsPresentation() =
        ApplicationManager.getApplication().invokeLater { actionToolbar.updateActionsImmediately() }

    override fun dispose() = Unit
}
