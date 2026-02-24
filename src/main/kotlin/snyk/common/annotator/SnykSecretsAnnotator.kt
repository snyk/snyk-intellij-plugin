package snyk.common.annotator

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiFile
import io.snyk.plugin.isSecretsRunning
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.ui.toolwindow.SnykPluginDisposable
import snyk.common.ProductType

class SnykSecretsAnnotator : SnykAnnotator(product = ProductType.SECRETS) {
  init {
    Disposer.register(SnykPluginDisposable.getInstance(), this)
  }

  override fun apply(
    psiFile: PsiFile,
    annotationResult: List<SnykAnnotation>,
    holder: AnnotationHolder,
  ) {
    if (disposed) return
    if (!pluginSettings().secretsEnabled) return
    if (isSecretsRunning(psiFile.project)) return
    super.apply(psiFile, annotationResult, holder)
  }
}
