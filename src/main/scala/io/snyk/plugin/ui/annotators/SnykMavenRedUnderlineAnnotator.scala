package io.snyk.plugin.ui.annotators

import com.intellij.lang.annotation.{AnnotationHolder, Annotator}
import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.PsiElement
import io.snyk.plugin.IntellijLogging.ScalaLogger
import io.snyk.plugin.datamodel.SecurityVuln
import io.snyk.plugin.ui.state.SnykPluginState

/**
  * Red underline annotator for Maven vulnerabilities.
  */
class SnykMavenRedUnderlineAnnotator extends Annotator {
  protected lazy val log: ScalaLogger = new ScalaLogger(Logger.getInstance(this.getClass))

  override def annotate(element: PsiElement, holder: AnnotationHolder): Unit = {
    val pluginState = SnykPluginState.forIntelliJ(element.getProject)

    val latestScanForSelectedProject = pluginState.latestScanForSelectedProject

    if (latestScanForSelectedProject.isEmpty) {
      return
    }

    latestScanForSelectedProject.get.foreach(snykVulnResponse => {
      snykVulnResponse.vulnerabilities.foreach(vulnerability => {
        val securityVulnerability = vulnerability.asInstanceOf[SecurityVuln]

        val vulnerabilityName = securityVulnerability.name
        val vulnerabilityNameParts = vulnerabilityName.split(":")

        vulnerabilityNameParts.foreach(name => {
          if (element.getText == name) {
            import com.intellij.openapi.util.TextRange

            val range = new TextRange(element.getTextRange.getStartOffset, element.getTextRange.getEndOffset)
            holder.createErrorAnnotation(range, "Vulnerable package")
          }
        })
      })
    })
  }
}
