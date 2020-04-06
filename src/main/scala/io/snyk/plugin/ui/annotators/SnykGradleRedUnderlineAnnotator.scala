package io.snyk.plugin.ui.annotators

import com.intellij.lang.annotation.{AnnotationHolder, ExternalAnnotator}
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.psi.{PsiComment, PsiElement, PsiFile, PsiRecursiveElementWalkingVisitor}
import io.snyk.plugin.IntellijLogging.ScalaLogger
import io.snyk.plugin.datamodel.SecurityVuln
import io.snyk.plugin.ui.state.SnykPluginState
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCommandArgumentList

import scala.collection.mutable

/**
  * Red underline annotator for Gradle vulnerabilities.
  */
class SnykGradleRedUnderlineAnnotator
  extends ExternalAnnotator[PsiFile, mutable.HashSet[AnnotationInfo]] {

  protected lazy val log: ScalaLogger = new ScalaLogger(Logger.getInstance(this.getClass))

  override def doAnnotate(psiFile: PsiFile): mutable.HashSet[AnnotationInfo] = {
    val annotationInfos = mutable.HashSet[AnnotationInfo]()

    val project = psiFile.getProject

    if (project == null) {
      return annotationInfos
    }

    val pluginState = SnykPluginState.getInstance(project)

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
                      if (element.isInstanceOf[PsiComment]) {
                        return
                      }

                      val securityVulnerability = vulnerability.asInstanceOf[SecurityVuln]

                      val dependencyInfos = securityVulnerability.name.split(":")

                      val group = dependencyInfos(0)
                      val name = dependencyInfos(1)
                      val version = securityVulnerability.version

                      val elementText = element.getText

                      if (elementText.contains(group)
                        && elementText.contains(name)
                        && elementText.contains(version)
                        && element.isInstanceOf[GrCommandArgumentList]
                        && !elementText.contains("{") && !elementText.contains("}")) {

                        annotationInfos += AnnotationInfo(s"Vulnerable package: $group:$name:$version", element)
                      }
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

case class AnnotationInfo(message: String, psiElement: PsiElement) {
  override def hashCode(): Int = message.hashCode

  override def equals(obj: Any): Boolean = {
    if (obj == null) {
      return false
    }

    if (!obj.isInstanceOf[AnnotationInfo]) {
      return false
    }

    obj.asInstanceOf[AnnotationInfo].message.equals(message)
  }
}
