package snyk.code.annotator

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.psi.PsiFile
import io.snyk.plugin.isSnykCodeRunning
import snyk.common.ProductType

class SnykCodeAnnotator : SnykAnnotator(product = ProductType.CODE_SECURITY) {

    override fun apply(psiFile: PsiFile, annotationResult: Unit, holder: AnnotationHolder) {
        if (isSnykCodeRunning(psiFile.project)) return
        super.apply(psiFile, annotationResult, holder)
    }
}
