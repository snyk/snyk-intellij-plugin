package io.snyk.plugin.ui.toolwindow

import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBUI
import io.snyk.plugin.ui.getReadOnlyClickableHtmlJEditorPane
import org.jetbrains.annotations.TestOnly
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.ScrollPaneConstants

class ReportFalsePositiveDialog(
    project: Project,
    private val titlePanel: JPanel
) : DialogWrapper(project) {

    @get:TestOnly
    val centerPanel: JPanel by lazy {
        val centerPanel = JPanel(BorderLayout(JBUIScale.scale(5), JBUIScale.scale(5)))

        // todo
        val editor = getReadOnlyClickableHtmlJEditorPane("Placeholder for the Editor")
        // EditorFactory.getInstance().createEditor()
        val scrollPane = JBScrollPane(
            editor,
            ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED
        )
        centerPanel.add(scrollPane, BorderLayout.CENTER)

        titlePanel.border = JBUI.Borders.empty()
        centerPanel.add(titlePanel, BorderLayout.NORTH)

        val warnMessageLabel = JBLabel(WARN_MESSAGE_TEXT).apply {
            icon = AllIcons.General.BalloonWarning
        }
        centerPanel.add(warnMessageLabel, BorderLayout.SOUTH)

        centerPanel.preferredSize = Dimension(600, 400)

        centerPanel
    }

    @get:TestOnly
    val actions: Array<Action> = arrayOf(cancelAction, ReportFalsePositiveAction())

    init {
        super.init() // don't remove! here `init()` is not Kotlin's `init{}` but method from super class
        title = TITLE_TEXT
    }

    override fun createCenterPanel(): JComponent = centerPanel

    override fun createActions(): Array<Action> = actions

    inner class ReportFalsePositiveAction : AbstractAction(REPORT_FALSE_POSITIVE_TEXT) {

        override fun actionPerformed(e: ActionEvent?) {
            // todo
            close(OK_EXIT_CODE)
        }
    }

    companion object {
        const val REPORT_FALSE_POSITIVE_TEXT = "Report False Positive"
        const val FALSE_POSITIVE_REPORTED_TEXT = "False Positive Reported"
        const val TITLE_TEXT = "Snyk - $REPORT_FALSE_POSITIVE_TEXT"
        const val WARN_MESSAGE_TEXT =
            "Please check the code. It will be uploaded to Snyk and manually reviewed by our engineers"
    }
}
