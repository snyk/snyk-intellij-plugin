package snyk.advisor

import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.psi.PsiCompiledElement
import com.intellij.psi.PsiDocumentManager

class AdvisorEditorFactoryListener: EditorFactoryListener {

    override fun editorCreated(event: EditorFactoryEvent) {

        val editor = event.editor

        // sanity checks, examples taken from com.intellij.codeInsight.preview.ImageOrColorPreviewManager.registerListeners
        if (editor.isOneLineMode) {
            return
        }

        val project = editor.project
        if (project == null || project.isDisposed) {
            return
        }

        val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.document)
        if (psiFile == null || psiFile.virtualFile == null || psiFile is PsiCompiledElement) {
            return
        }

        if (editor !is EditorEx) {
            return
        }

        val advisorScoreProvider = AdvisorScoreProvider(editor)

        editor.registerLineExtensionPainter(advisorScoreProvider::getLineExtensions)

    }
}
