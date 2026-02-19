package snyk.common.annotator

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiFile
import io.snyk.plugin.isSnykCodeRunning
import io.snyk.plugin.ui.toolwindow.SnykPluginDisposable
import snyk.common.ProductType

class SnykCodeAnnotator : SnykAnnotator(product = ProductType.CODE_SECURITY) {
  init {
    Disposer.register(SnykPluginDisposable.getInstance(), this)
  }

  override fun apply(
    psiFile: PsiFile,
    annotationResult: List<SnykAnnotation>,
    holder: AnnotationHolder,
  ) {
    if (disposed) return
    if (isSnykCodeRunning(psiFile.project)) return
    super.apply(psiFile, annotationResult, holder)
  }
}
