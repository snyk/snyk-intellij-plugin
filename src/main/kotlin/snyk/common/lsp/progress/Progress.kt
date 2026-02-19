/**
 * ****************************************************************************
 * Copyright (c) 2024 Red Hat, Inc. Copyright (c) 2024 Snyk Ltd
 *
 * Distributed under license by Red Hat, Inc. All rights reserved. This program is made available
 * under the terms of the Eclipse Public License v2.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors: Red Hat, Inc. - initial API and implementation Snyk Ltd - adjustments for use in
 * Snyk IntelliJ Plugin
 * *****************************************************************************
 */
package snyk.common.lsp.progress

import com.intellij.openapi.project.Project
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.TimeUnit
import org.eclipse.lsp4j.WorkDoneProgressCancelParams
import org.eclipse.lsp4j.WorkDoneProgressNotification
import snyk.common.lsp.LanguageServerWrapper

internal class Progress(val token: String, val project: Project) {
  var cancellable: Boolean = false
  var done: Boolean = false

  private val progressNotifications = LinkedBlockingDeque<WorkDoneProgressNotification>()

  var cancelled: Boolean = false
    private set

  var title: String? = null
    get() = if (field != null) field else token

  fun add(progressNotification: WorkDoneProgressNotification) {
    progressNotifications.add(progressNotification)
  }

  @get:Throws(InterruptedException::class)
  val nextProgressNotification: WorkDoneProgressNotification?
    get() = progressNotifications.pollFirst(200, TimeUnit.MILLISECONDS)

  fun cancel() {
    this.cancelled = true
    // Run async to avoid blocking when cancelling progress
    org.jetbrains.concurrency.runAsync {
      try {
        val workDoneProgressCancelParams = WorkDoneProgressCancelParams()
        workDoneProgressCancelParams.setToken(token)
        val languageServerWrapper = LanguageServerWrapper.getInstance(project)
        if (languageServerWrapper.isInitialized && !project.isDisposed) {
          val languageServer = languageServerWrapper.languageServer
          languageServer.cancelProgress(workDoneProgressCancelParams)
        }
      } catch (ignored: Exception) {
        // Ignore errors during cancel - the progress is being cancelled anyway
      }
    }
  }
}
