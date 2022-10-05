@file:JvmName("TrustedProjectsKt")
package snyk.trust

import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.ui.Messages
import snyk.SnykBundle
import java.nio.file.Files
import java.nio.file.Path

private val LOG = Logger.getInstance("snyk.trust.TrustedProjects")

/**
 * Shows the "Trust and Scan Project?" dialog, if the user wasn't asked yet if they trust this project,
 * and sets the project trusted state according to the user choice.
 *
 * @return `false` if the user chose not to scan the project at all; `true` otherwise
 */
fun confirmScanningAndSetWorkspaceTrustedStateIfNeeded(projectFileOrDir: Path): Boolean {
    val projectDir = if (Files.isDirectory(projectFileOrDir)) projectFileOrDir else projectFileOrDir.parent

    val trustService = service<WorkspaceTrustService>()
    val trustedState = trustService.isPathTrusted(projectDir)
    if (!trustedState) {
        LOG.info("Asking user to trust the project ${projectDir.fileName}")
        return when (confirmScanningUntrustedProject(projectDir)) {
            ScanUntrustedProjectChoice.TRUST_AND_SCAN -> {
                trustService.addTrustedPath(projectDir)
                true
            }

            ScanUntrustedProjectChoice.CANCEL -> false
        }
    }

    return true
}

private fun confirmScanningUntrustedProject(projectDir: Path): ScanUntrustedProjectChoice {
    val fileName = projectDir.fileName ?: projectDir.toString()
    val title = SnykBundle.message("snyk.trust.dialog.warning.title", fileName)
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
            .show()

        choice = if (result == Messages.YES) {
            LOG.info("User trusts the project $fileName for scans")
            ScanUntrustedProjectChoice.TRUST_AND_SCAN
        } else {
            LOG.info("User doesn't trust the project $fileName for scans")
            ScanUntrustedProjectChoice.CANCEL
        }
    }

    return choice
}

enum class ScanUntrustedProjectChoice {
    TRUST_AND_SCAN,
    CANCEL;
}
