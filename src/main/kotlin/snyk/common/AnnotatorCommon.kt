package snyk.common

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.psi.PsiFile

object AnnotatorCommon {
    val logger = logger<AnnotatorCommon>()
    fun prepareAnnotate(psiFile: PsiFile?) {
        logger.debug("Preparing annotation for $psiFile")
        val document = psiFile?.viewProvider?.document ?: return
        ApplicationManager.getApplication().invokeLater {
            FileDocumentManager.getInstance().saveDocument(document)
        }
    }
}
