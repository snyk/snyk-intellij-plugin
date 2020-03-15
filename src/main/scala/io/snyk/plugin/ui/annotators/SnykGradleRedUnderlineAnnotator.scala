package io.snyk.plugin.ui.annotators

import com.intellij.lang.annotation.{Annotation, AnnotationHolder, Annotator}
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.TextRange
import com.intellij.psi.{PsiComment, PsiElement}
import com.intellij.util.SmartList
import io.snyk.plugin.IntellijLogging.ScalaLogger
import io.snyk.plugin.datamodel.SecurityVuln
import io.snyk.plugin.ui.state.SnykPluginState

/**
  * Red underline annotator for Gradle vulnerabilities.
  */
class SnykGradleRedUnderlineAnnotator extends Annotator {
  protected lazy val log: ScalaLogger = new ScalaLogger(Logger.getInstance(this.getClass))

  def checkAnnotationNotExists(holder: AnnotationHolder, textRange: TextRange): Boolean = {
    val annotations: SmartList[Annotation] = holder.asInstanceOf[SmartList[Annotation]]

    annotations.forEach(annotation => {
      if (annotation.getStartOffset == textRange.getStartOffset
        && annotation.getEndOffset == textRange.getEndOffset) {
        return false
      }
    })

    true
  }


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
              val textRange = element.getTextRange

              if (elementText.contains(group)
                && elementText.contains(name)
                && elementText.contains(securityVulnerability.version)
                && !elementText.contains("{") && !elementText.contains("}")
                && checkAnnotationNotExists(holder, textRange)) {
                val range = new TextRange(textRange.getStartOffset, textRange.getEndOffset)
                holder.createErrorAnnotation(range, "Vulnerable package")
              }
            })
          })
        })
      case None => log.debug("No vulnerabilities for annotation.")
    }
  }
}
