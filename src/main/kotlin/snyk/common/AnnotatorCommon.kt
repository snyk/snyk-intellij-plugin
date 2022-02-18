package snyk.common

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.psi.PsiFile
import snyk.oss.annotator.AnnotatorHelper

object AnnotatorCommon {
    fun prepareAnnotate(psiFile: PsiFile?) {
        val filePath = psiFile?.virtualFile?.path ?: return

        if (AnnotatorHelper.isFileSupported(filePath)) {
            val document = psiFile.viewProvider.document ?: return
            ApplicationManager.getApplication().invokeLater {
                FileDocumentManager.getInstance().saveDocument(document)
            }
        }
    }
}
