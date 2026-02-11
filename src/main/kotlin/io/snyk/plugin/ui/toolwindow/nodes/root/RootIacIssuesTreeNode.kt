package io.snyk.plugin.ui.toolwindow.nodes.root

import com.intellij.openapi.project.Project
import io.snyk.plugin.getSnykCachedResults
import io.snyk.plugin.ui.toolwindow.SnykToolWindowPanel
import snyk.common.SnykError

class RootIacIssuesTreeNode(project: Project) :
  RootTreeNodeBase(SnykToolWindowPanel.IAC_ROOT_TEXT, project) {

  override fun getNoVulnerabilitiesMessage(): String =
    if ((this.userObject as String).endsWith(SnykToolWindowPanel.NO_SUPPORTED_IAC_FILES_FOUND)) {
      SnykToolWindowPanel.NO_IAC_FILES
    } else {
      super.getNoVulnerabilitiesMessage()
    }

  override fun getSelectVulnerabilityMessage(): String =
    if ((this.userObject as String).endsWith(SnykToolWindowPanel.NO_SUPPORTED_IAC_FILES_FOUND)) {
      SnykToolWindowPanel.NO_IAC_FILES
    } else {
      super.getSelectVulnerabilityMessage()
    }

  override fun getSnykError(): SnykError? =
    getSnykCachedResults(project)?.currentIacError?.toSnykError()
}
