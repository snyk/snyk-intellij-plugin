package io.snyk.plugin.snykcode.core

import ai.deepcode.javaclient.core.HashContentUtilsBase
import com.intellij.openapi.util.Computable
import com.intellij.psi.PsiFile

class HashContentUtils private constructor() : HashContentUtilsBase(
    PDU.instance
) {
    override fun doGetFileContent(file: Any): String {
        val psiFile = PDU.toPsiFile(file)
        return RunUtils.computeInReadActionInSmartMode(
            psiFile.project,
            Computable { getPsiFileText(psiFile) }
        ) ?: ""
    }

    /** Should be run inside **Read action** !!!  */
    private fun getPsiFileText(psiFile: PsiFile): String {
        if (!psiFile.isValid) {
            SCLogger.instance.logWarn("Invalid PsiFile: $psiFile")
            return ""
        }
        // psiFile.getText() is NOT expensive as it's goes to VirtualFileContent.getText()
        return psiFile.text
    }

    companion object {
        val instance = HashContentUtils()
    }
}
