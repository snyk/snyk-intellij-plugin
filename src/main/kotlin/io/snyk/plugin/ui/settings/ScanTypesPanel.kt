package io.snyk.plugin.ui.settings

import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.layout.panel
import io.snyk.plugin.getApplicationSettingsStateService
import javax.swing.JLabel

class ScanTypesPanel(
    cliScanComments: String? = null,
    snykCodeScanComments: String? = null,
    snykCodeQualityIssueCheckboxVisible: Boolean = true
) {

    var snykCodeCheckbox: JBCheckBox? = null
    var snykCodeQualityCheckbox: JBCheckBox? = null
    var snykCodeComment: JLabel? = null

    val panel = panel {
        row {
            checkBox(
                "Snyk OpenSource vulnerabilities",
                { getApplicationSettingsStateService().cliScanEnable },
                { getApplicationSettingsStateService().cliScanEnable = it },
                cliScanComments
            )
        }
        row {
            snykCodeCheckbox = checkBox(
                "Snyk Code Security issues",
                { getApplicationSettingsStateService().snykCodeSecurityIssuesScanEnable },
                { getApplicationSettingsStateService().snykCodeSecurityIssuesScanEnable = it }
            )
                .component

            if (snykCodeQualityIssueCheckboxVisible) {
                snykCodeQualityCheckbox = checkBox(
                    "Snyk Code Quality issues",
                    { getApplicationSettingsStateService().snykCodeQualityIssuesScanEnable },
                    { getApplicationSettingsStateService().snykCodeQualityIssuesScanEnable = it }
                )
                    .withLargeLeftGap()
                    .component
            }
        }
        row {
            snykCodeComment = label(snykCodeScanComments ?: "").component
        }
    }
}
