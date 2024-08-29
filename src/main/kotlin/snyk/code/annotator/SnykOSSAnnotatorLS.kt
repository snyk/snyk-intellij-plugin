package snyk.code.annotator

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiFile
import io.snyk.plugin.isOssRunning
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.ui.toolwindow.SnykPluginDisposable
import snyk.common.ProductType

class SnykOSSAnnotatorLS : SnykAnnotator(product = ProductType.OSS) {
    init {
        Disposer.register(SnykPluginDisposable.getInstance(), this)
    }

    override fun apply(
        psiFile: PsiFile,
        annotationResult: List<SnykAnnotation>,
        holder: AnnotationHolder,
    ) {
        if (disposed) return
        if (!pluginSettings().ossScanEnable) return
        if (isOssRunning(psiFile.project)) return

        super.apply(psiFile, annotationResult, holder)
    }
}
