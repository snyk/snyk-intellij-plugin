package snyk.oss.annotator

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import snyk.oss.Vulnerability
import snyk.oss.annotator.AnnotatorHelper.hasDedicatedAnnotator

open class OSSDefaultAnnotator : OSSBaseAnnotator() {
    override fun apply(psiFile: PsiFile, annotationResult: Unit, holder: AnnotationHolder) {
        // only trigger if no dedicated annotators available
        if (hasDedicatedAnnotator(psiFile.virtualFile.path)) return
        super.apply(psiFile, annotationResult, holder)
    }

    override fun fixRange(psiFile: PsiFile, vulnerability: Vulnerability): TextRange {
        return TextRange.EMPTY_RANGE // don't offer fixes in default annotator
    }
}
