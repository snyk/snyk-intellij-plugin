package io.snyk.plugin.extensions

import com.intellij.openapi.project.Project
import io.snyk.plugin.getSnykTaskQueueService
import snyk.common.lsp.LanguageServerWrapper

/** SnykController is used by third-party plugins to interact with the Snyk plugin. */
class SnykControllerImpl(val project: Project) : SnykController {

  /** scan enqueues a scan of the project for vulnerabilities. */
  override fun scan() {
    getSnykTaskQueueService(project)?.scan()
  }

  /**
   * userId returns the current authenticated Snyk user's ID.
   *
   * If no user is authenticated, this will return null.
   */
  override fun userId(): String? = LanguageServerWrapper.getInstance(project).getAuthenticatedUser()
}
