@file:JvmName("TrustedProjectsKt")

package snyk.trust

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.ui.Messages
import io.snyk.plugin.getContentRootPaths
import io.snyk.plugin.toVirtualFile
import snyk.SnykBundle

private val LOG = Logger.getInstance("snyk.trust.TrustedProjects")

/**
 * Shows the "Trust and Scan Project?" dialog, if the user wasn't asked yet if they trust this project,
 * and sets the project trusted state according to the user choice.
 *
 * @return `false` if the user chose not to scan the project at all; `true` otherwise
 */
fun confirmScanningAndSetWorkspaceTrustedStateIfNeeded(project: Project): Boolean {
    if (project.isDisposed || ApplicationManager.getApplication().isDisposed) return false
    val paths = project.getContentRootPaths().toMutableSet()
    val basePath = project.basePath

    // fallback to project base path if no content roots (needed for rider)
    if (paths.isEmpty() && basePath != null) {
        paths.add(basePath.toVirtualFile().toNioPath())
    }

    val trustService = service<WorkspaceTrustService>()
    for (path in paths) {
        val trustedState = trustService.isPathTrusted(path)
        if (trustedState) continue

        LOG.info("Asking user to trust the project ${project.name}")
        val trustProject = confirmScanningUntrustedProject(project)
        return when (trustProject) {
            ScanUntrustedProjectChoice.TRUST_AND_SCAN -> {
                paths.forEach { p -> trustService.addTrustedPath(p) }
                true
            }

            ScanUntrustedProjectChoice.CANCEL -> false
        }
    }
    return true
}

private fun confirmScanningUntrustedProject(project: Project): ScanUntrustedProjectChoice {
    val title = SnykBundle.message("snyk.trust.dialog.warning.title", project.name)
    val message = SnykBundle.message("snyk.trust.dialog.warning.text")
    val trustButton = SnykBundle.message("snyk.trust.dialog.warning.button.trust")
    val distrustButton = SnykBundle.message("snyk.trust.dialog.warning.button.distrust")

    var choice = ScanUntrustedProjectChoice.CANCEL

    invokeAndWaitIfNeeded {
        val result = MessageDialogBuilder
            .yesNo(title, message)
            .icon(Messages.getWarningIcon())
            .yesText(trustButton)
            .noText(distrustButton)
            .ask(project)

        choice = if (result) {
            LOG.info("User trusts the project $project for scans")
            ScanUntrustedProjectChoice.TRUST_AND_SCAN
        } else {
            LOG.info("User doesn't trust the project $project for scans")
            ScanUntrustedProjectChoice.CANCEL
        }
    }

    return choice
}

enum class ScanUntrustedProjectChoice {
    TRUST_AND_SCAN,
    CANCEL;
}
