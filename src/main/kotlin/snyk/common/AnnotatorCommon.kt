package snyk.common

import com.intellij.openapi.diagnostic.logger
import com.intellij.psi.PsiFile

object AnnotatorCommon {
    val logger = logger<AnnotatorCommon>()

    fun prepareAnnotate(psiFile: PsiFile?) {
        logger.debug("Preparing annotation for $psiFile")
        // todo: review later if any way to provide up-to-date context for CLI scans is available
        // force saving here will break some user's workflow: https://github.com/snyk/snyk-intellij-plugin/issues/324
    }
}
