package io.snyk.plugin.ui.toolwindow

import com.intellij.icons.AllIcons
import com.intellij.lang.LanguageCommenters
import com.intellij.openapi.editor.DisposableEditorPanel
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Iconable
import com.intellij.psi.PsiFile
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.TestOnly
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.JPanel

class ReportFalsePositiveDialog(
    private val project: Project,
    private val titlePanel: JPanel,
    private val psiFiles: Set<PsiFile>
) : DialogWrapper(project) {

    private val editors: MutableList<Editor> = mutableListOf()
    var result: String = ""

    @get:TestOnly
    val centerPanel: JPanel by lazy {
        val centerPanel = JPanel(BorderLayout(JBUIScale.scale(5), JBUIScale.scale(5)))

        centerPanel.add(getTabbedPane(), BorderLayout.CENTER)

        titlePanel.border = JBUI.Borders.empty()
        centerPanel.add(titlePanel, BorderLayout.NORTH)

        val warnMessageLabel = JBLabel(WARN_MESSAGE_TEXT).apply {
            icon = AllIcons.General.BalloonWarning
        }
        centerPanel.add(warnMessageLabel, BorderLayout.SOUTH)

        centerPanel.preferredSize = Dimension(800, 600)

        centerPanel
    }

    private fun getTabbedPane(): JBTabbedPane {
        val tabbedPane = JBTabbedPane()
        tabbedPane.tabComponentInsets = JBInsets.create(0, 0) // no inner borders for tab content

        psiFiles.forEach { psiFile ->
            val editorFactory = EditorFactory.getInstance()
            val textWithHeader =
                getLineCommentPrefix(psiFile) + " Code from ${psiFile.virtualFile.path}\n\n" + psiFile.text
            val document = editorFactory.createDocument(textWithHeader)
            val editor = editorFactory.createEditor(document, project, psiFile.fileType, false)
            editors.add(editor)

            val editorPanel = DisposableEditorPanel(editor)
            Disposer.register(disposable, editorPanel)

            tabbedPane.addTab(
                psiFile.virtualFile.name,
                psiFile.getIcon(Iconable.ICON_FLAG_READ_STATUS),
                editorPanel,
                psiFile.virtualFile.path
            )
        }
        return tabbedPane
    }

    @get:TestOnly
    val actions: Array<Action> = arrayOf(cancelAction, ReportFalsePositiveAction())

    init {
        super.init() // don't remove! here `init()` is not Kotlin's `init{}` but method from super class
        title = TITLE_TEXT
    }

    override fun createCenterPanel(): JComponent = centerPanel

    override fun createActions(): Array<Action> = actions

    private fun getLineCommentPrefix(psiFile: PsiFile): String =
        LanguageCommenters.INSTANCE.forLanguage(psiFile.language)?.lineCommentPrefix
            ?: guessDefaultLineCommentPrefix(psiFile)

    private fun guessDefaultLineCommentPrefix(psiFile: PsiFile): String =
        // hack for Python files in case Python support is not installed (Idea CE by default)
        if (psiFile.virtualFile.extension == "py") "#" else DEFAULT_LINE_COMMENT_PREFIX

    inner class ReportFalsePositiveAction : AbstractAction(REPORT_FALSE_POSITIVE_TEXT) {

        override fun actionPerformed(e: ActionEvent?) {
            result = editors.joinToString(
                separator = "\n\n"
            ) { it.document.charsSequence }
            close(OK_EXIT_CODE)
        }
    }

    companion object {
        const val REPORT_FALSE_POSITIVE_TEXT = "Report False Positive"
        const val FALSE_POSITIVE_REPORTED_TEXT = "False Positive Reported"
        const val TITLE_TEXT = "Snyk - $REPORT_FALSE_POSITIVE_TEXT"
        const val WARN_MESSAGE_TEXT =
            "Please check the code. It will be uploaded to Snyk and manually reviewed by our engineers"
        private const val DEFAULT_LINE_COMMENT_PREFIX = "//"
    }
}
