package snyk.common.annotator

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiFile
import io.snyk.plugin.isIacRunning
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.ui.toolwindow.SnykPluginDisposable
import snyk.common.ProductType

class SnykIaCAnnotator : SnykAnnotator(product = ProductType.IAC) {
  init {
    Disposer.register(SnykPluginDisposable.getInstance(), this)
  }

  override fun apply(
    psiFile: PsiFile,
    annotationResult: List<SnykAnnotation>,
    holder: AnnotationHolder,
  ) {
    if (disposed) return
    if (!pluginSettings().iacScanEnabled) return
    if (isIacRunning(psiFile.project)) return

    super.apply(psiFile, annotationResult, holder)
  }
}
