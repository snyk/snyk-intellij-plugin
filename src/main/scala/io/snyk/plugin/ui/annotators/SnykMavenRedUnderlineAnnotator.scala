package io.snyk.plugin.ui.annotators

import com.intellij.lang.annotation.{AnnotationHolder, ExternalAnnotator}
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.psi.{PsiElement, PsiFile, PsiRecursiveElementWalkingVisitor}
import io.snyk.plugin.IntellijLogging.ScalaLogger
import io.snyk.plugin.datamodel.SecurityVuln
import io.snyk.plugin.ui.state.SnykPluginState

import scala.collection.mutable

/**
  * Red underline annotator for Maven vulnerabilities.
  */
class SnykMavenRedUnderlineAnnotator extends ExternalAnnotator[PsiFile, mutable.HashSet[AnnotationInfo]] {

  protected lazy val log: ScalaLogger = new ScalaLogger(Logger.getInstance(this.getClass))

  override def doAnnotate(psiFile: PsiFile): mutable.HashSet[AnnotationInfo] = {
    val annotationInfos = mutable.HashSet[AnnotationInfo]()

    val project = psiFile.getProject

    if (project == null) {
      return annotationInfos
    }

    val pluginState = SnykPluginState.forIntelliJ(project)

    val latestScanForSelectedProject = pluginState.latestScanForSelectedProject

    if (latestScanForSelectedProject.isEmpty) {
      return annotationInfos
    }

    ApplicationManager.getApplication.runReadAction(new Runnable {
      override def run(): Unit = {
        psiFile.accept(new PsiRecursiveElementWalkingVisitor() {
          override def visitElement(element: PsiElement): Unit = {
            latestScanForSelectedProject match {
              case Some(vulnerabilitiesResponse) =>
                vulnerabilitiesResponse.foreach(snykVulnResponse => {
                  snykVulnResponse.vulnerabilities.seq.foreach(vulnerabilities => {
                    vulnerabilities.foreach(vulnerability => {
                      val securityVulnerability = vulnerability.asInstanceOf[SecurityVuln]

                      val fullName = securityVulnerability.name
                      val version = securityVulnerability.version
                      val nameParts = fullName.split(":")

                      val elementText = element.getText

                      nameParts.foreach(name => {
                        if (elementText == name) {
                          annotationInfos += AnnotationInfo(s"Vulnerable package: $name:$version", element)
                        }
                      })
                    })
                  })
                })
              case None => log.debug("No vulnerabilities for annotation.")
            }

            super.visitElement(element)
          }
        })
      }
    })

    annotationInfos
  }

  override def collectInformation(psiFile: PsiFile, editor: Editor, hasErrors: Boolean): PsiFile = {
    psiFile
  }

  override def apply(psiFile: PsiFile,
    annotationInfos: mutable.HashSet[AnnotationInfo],
    annotationHolder: AnnotationHolder): Unit = {

    annotationInfos.foreach(annotationInfo => {
      val psiElement = annotationInfo.psiElement

      if (psiElement != null) {
        annotationHolder.createErrorAnnotation(psiElement, annotationInfo.message)
      }
    })
  }
}
