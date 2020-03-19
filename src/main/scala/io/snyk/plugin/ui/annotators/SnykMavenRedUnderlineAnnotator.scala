package io.snyk.plugin.ui.annotators

import com.intellij.lang.annotation.{AnnotationHolder, ExternalAnnotator}
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.psi.xml.XmlText
import com.intellij.psi.{PsiElement, PsiFile, PsiRecursiveElementWalkingVisitor}
import io.snyk.plugin.IntellijLogging.ScalaLogger
import io.snyk.plugin.datamodel.SecurityVuln
import io.snyk.plugin.ui.state.SnykPluginState

import scala.collection.mutable

/**
  * Red underline annotator for Maven vulnerabilities.
  */
class SnykMavenRedUnderlineAnnotator extends ExternalAnnotator[PsiFile, mutable.HashSet[MavenAnnotationInfo]] {

  protected lazy val log: ScalaLogger = new ScalaLogger(Logger.getInstance(this.getClass))

  override def doAnnotate(psiFile: PsiFile): mutable.HashSet[MavenAnnotationInfo] = {
    val annotationInfos = mutable.HashSet[MavenAnnotationInfo]()

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
                      val nameParts = fullName.split(":")

                      val name = nameParts(0)
                      val group = nameParts(1)
                      val version = securityVulnerability.version

                      val elementText = element.getText

                      if (elementText.contains("dependency")
                        && elementText.contains(name)
                        && elementText.contains(group)
                        && !elementText.contains("dependencies")) {
                        annotationInfos += new MavenAnnotationInfo(element, name, group, version)
                      }

                      securityVulnerability.from.foreach(fromFullModuleName => {
                        val moduleNameParts = fromFullModuleName.split(":")

                        val fromModuleName = moduleNameParts(0)

                        val moduleGroupParts = moduleNameParts(1).split("@")
                        val fromModuleGroup = moduleGroupParts(0)
                        val fromModuleVersion = moduleGroupParts(1)

                        if (elementText.contains("dependency")
                          && elementText.contains(fromModuleName)
                          && elementText.contains(fromModuleGroup)
                          && !elementText.contains("dependencies")) {
                          annotationInfos += new MavenAnnotationInfo(element, fromModuleName, fromModuleGroup, fromModuleVersion)
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
    annotationInfos: mutable.HashSet[MavenAnnotationInfo],
    annotationHolder: AnnotationHolder): Unit = {

    annotationInfos.foreach(annotationInfo => {
      val parentPsiElement = annotationInfo.psiElement

      if (parentPsiElement != null) {
        parentPsiElement.accept(new PsiRecursiveElementWalkingVisitor() {
          override def visitElement(element: PsiElement): Unit = {
            if (element.isInstanceOf[XmlText]) {
              val elementText = element.getText

              if (elementText == annotationInfo.name) {
                annotationHolder.createErrorAnnotation(element, annotationInfo.message)
              }

              if (elementText == annotationInfo.group) {
                annotationHolder.createErrorAnnotation(element, annotationInfo.message)
              }


              if (elementText == annotationInfo.version || (elementText.contains("${") && elementText.contains("}"))) {
                annotationHolder.createErrorAnnotation(element, annotationInfo.message)
              }
            }

            super.visitElement(element)
          }
        })
      }
    })
  }
}

class MavenAnnotationInfo(aPsiElement: PsiElement, aName: String, aGroup: String, aVersion: String) {

  val psiElement: PsiElement = aPsiElement

  val message = s"Vulnerable package: $aName:$aGroup:$aVersion"

  val name: String = aName
  val group: String = aGroup
  val version: String = aVersion

  def canEqual(other: Any): Boolean = other.isInstanceOf[MavenAnnotationInfo]

  override def equals(other: Any): Boolean = other match {
    case that: MavenAnnotationInfo => (that canEqual this) && message == that.message
    case _ => false
  }

  override def hashCode(): Int = {
    val state = Seq(message)
    state.map(_.hashCode()).foldLeft(0)((a, b) => 31 * a + b)
  }
}
