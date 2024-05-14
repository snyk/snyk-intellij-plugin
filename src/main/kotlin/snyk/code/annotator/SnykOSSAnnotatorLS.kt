package snyk.code.annotator

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.psi.PsiFile
import io.snyk.plugin.*
import snyk.common.ProductType

class SnykOSSAnnotatorLS : SnykAnnotator(product = ProductType.OSS) {

    override fun apply(psiFile: PsiFile, annotationResult: Unit, holder: AnnotationHolder) {
        if (!isSnykOSSLSEnabled()) return
        if (isOssRunning(psiFile.project)) return

        super.apply(psiFile, annotationResult, holder)
    }
}
