package io.snyk.plugin.ui.annotators

import com.intellij.lang.annotation.{AnnotationHolder, Annotator}
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.TextRange
import com.intellij.psi.{PsiComment, PsiElement}
import io.snyk.plugin.IntellijLogging.ScalaLogger
import io.snyk.plugin.datamodel.SecurityVuln
import io.snyk.plugin.ui.state.SnykPluginState

/**
  * Red underline annotator for Gradle vulnerabilities.
  */
class SnykGradleRedUnderlineAnnotator extends Annotator {
  protected lazy val log: ScalaLogger = new ScalaLogger(Logger.getInstance(this.getClass))

  override def annotate(element: PsiElement, holder: AnnotationHolder): Unit = {
    val pluginState = SnykPluginState.forIntelliJ(element.getProject)

    val latestScanForSelectedProject = pluginState.latestScanForSelectedProject

    if (latestScanForSelectedProject.isEmpty) {
      return
    }

    latestScanForSelectedProject match {
      case Some(vulnerabilitiesResponse) =>
        vulnerabilitiesResponse.foreach(snykVulnResponse => {
          snykVulnResponse.vulnerabilities.seq.foreach(vulnerabilities => {
            vulnerabilities.foreach(vulnerability => {
              if (element.isInstanceOf[PsiComment]) {
                return
              }

              val securityVulnerability = vulnerability.asInstanceOf[SecurityVuln]

              val dependencyInfos = securityVulnerability.name.split(":")

              val group = dependencyInfos(0)
              val name = dependencyInfos(1)

              val elementText = element.getText

              if (elementText.contains(group)
                && elementText.contains(name)
                && elementText.contains(securityVulnerability.version)
                && !elementText.contains("{") && !elementText.contains("}")) {
                val range = new TextRange(element.getTextRange.getStartOffset, element.getTextRange.getEndOffset)
                holder.createErrorAnnotation(range, "Vulnerable package")
              }
            })
          })
        })
      case None => log.debug("No vulnerabilities for annotation.")
    }
  }
}
