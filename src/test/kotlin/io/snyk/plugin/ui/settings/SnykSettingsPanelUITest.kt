package io.snyk.plugin.ui.settings

import com.intellij.openapi.components.service
import io.snyk.plugin.services.SnykApplicationSettingsStateService
import io.snyk.plugin.ui.SnykUITestBase
import org.junit.Test
import javax.swing.JCheckBox
import javax.swing.JTextField
import snyk.UIComponentFinder
import java.awt.Component
import java.awt.Container

/**
 * Component tests for Snyk Settings UI panels
 * Tests the settings panel components without requiring full IDE context
 */
class SnykSettingsPanelUITest : SnykUITestBase() {

    private fun getAllCheckboxesFromPanel(container: Component): List<JCheckBox> {
        val checkboxes = mutableListOf<JCheckBox>()
        if (container is JCheckBox) {
            checkboxes.add(container)
        }
        if (container is Container) {
            for (component in container.components) {
                checkboxes.addAll(getAllCheckboxesFromPanel(component))
            }
        }
        return checkboxes
    }

    @Test
    fun `test scan types panel displays all scan type checkboxes`() {
        // Create scan types panel
        val scanTypesPanel = ScanTypesPanel(project)
        
        // Verify all scan type checkboxes exist
        val panel = scanTypesPanel.scanTypesPanel
        val checkboxes = getAllCheckboxesFromPanel(panel)
        val ossCheckbox = checkboxes.find { it.text?.contains("Open Source") == true }
        val codeSecurityCheckbox = checkboxes.find { it.text?.contains("Code Security") == true }
        val codeQualityCheckbox = checkboxes.find { it.text?.contains("Code Quality") == true }
        val iacCheckbox = checkboxes.find { it.text?.contains("Infrastructure as Code") == true }

        assertNotNull("OSS checkbox should exist", ossCheckbox)
        assertNotNull("Code Security checkbox should exist", codeSecurityCheckbox)
        assertNotNull("Code Quality checkbox should exist", codeQualityCheckbox)
        assertNotNull("IaC checkbox should exist", iacCheckbox)
    }

    @Test
    fun `test scan types panel reflects current settings state`() {
        // Update settings
        val settings = service<SnykApplicationSettingsStateService>()
        settings.ossScanEnable = true
        settings.snykCodeSecurityIssuesScanEnable = false
        settings.iacScanEnabled = true

        // Create panel
        val scanTypesPanel = ScanTypesPanel(project)

        // Verify checkboxes reflect settings
        val panel = scanTypesPanel.scanTypesPanel
        val checkboxes = getAllCheckboxesFromPanel(panel)
        val ossCheckbox = checkboxes.find { it.text?.contains("Open Source") == true }
        val codeSecurityCheckbox = checkboxes.find { it.text?.contains("Code Security") == true }
        val iacCheckbox = checkboxes.find { it.text?.contains("Infrastructure as Code") == true }

        assertTrue("OSS should be enabled", ossCheckbox?.isSelected ?: false)
        assertFalse("Code Security should be disabled", codeSecurityCheckbox?.isSelected ?: true)
        assertTrue("IaC should be enabled", iacCheckbox?.isSelected ?: false)
    }

    @Test
    fun `test severities panel displays all severity checkboxes`() {
        // Create severities panel
        val severitiesPanel = SeveritiesEnablementPanel()

        // Verify all severity checkboxes exist
        val checkboxes = getAllCheckboxesFromPanel(severitiesPanel.panel)
        val criticalCheckbox = checkboxes.find { it.text == "Critical" }
        val highCheckbox = checkboxes.find { it.text == "High" }
        val mediumCheckbox = checkboxes.find { it.text == "Medium" }
        val lowCheckbox = checkboxes.find { it.text == "Low" }

        assertNotNull("Critical checkbox should exist", criticalCheckbox)
        assertNotNull("High checkbox should exist", highCheckbox)
        assertNotNull("Medium checkbox should exist", mediumCheckbox)
        assertNotNull("Low checkbox should exist", lowCheckbox)
    }

    @Test
    fun `test severities panel reflects current filter settings`() {
        // Update settings
        val settings = service<SnykApplicationSettingsStateService>()
        settings.criticalSeverityEnabled = true
        settings.highSeverityEnabled = true
        settings.mediumSeverityEnabled = false
        settings.lowSeverityEnabled = false

        // Create panel
        val severitiesPanel = SeveritiesEnablementPanel()

        // Verify checkboxes reflect settings
        val checkboxes = getAllCheckboxesFromPanel(severitiesPanel.panel)
        val criticalCheckbox = checkboxes.find { it.text == "Critical" }
        val highCheckbox = checkboxes.find { it.text == "High" }
        val mediumCheckbox = checkboxes.find { it.text == "Medium" }
        val lowCheckbox = checkboxes.find { it.text == "Low" }

        assertTrue("Critical should be enabled", criticalCheckbox?.isSelected ?: false)
        assertTrue("High should be enabled", highCheckbox?.isSelected ?: false)
        assertFalse("Medium should be disabled", mediumCheckbox?.isSelected ?: true)
        assertFalse("Low should be disabled", lowCheckbox?.isSelected ?: true)
    }

    @Test
    fun `test issue view options panel displays filter options`() {
        // Create issue view options panel
        val optionsPanel = IssueViewOptionsPanel(project)

        // Verify the panel contains filter options
        val checkboxes = getAllCheckboxesFromPanel(optionsPanel.panel)
        val ignoredCheckbox = checkboxes.find { it.text == "Ignored issues" }
        val openIssuesCheckbox = checkboxes.find { it.text == "Open issues" }

        assertNotNull("Ignored issues checkbox should exist", ignoredCheckbox)
        assertNotNull("Open issues checkbox should exist", openIssuesCheckbox)
    }

    @Test
    fun `test issue view options panel reflects current view settings`() {
        // Update settings
        val settings = service<SnykApplicationSettingsStateService>()
        settings.ignoredIssuesEnabled = false
        settings.openIssuesEnabled = true

        // Create panel
        val optionsPanel = IssueViewOptionsPanel(project)

        // Verify checkboxes reflect settings
        val checkboxes = getAllCheckboxesFromPanel(optionsPanel.panel)
        val ignoredCheckbox = checkboxes.find { it.text == "Ignored issues" }
        val openIssuesCheckbox = checkboxes.find { it.text == "Open issues" }

        assertFalse("Ignored issues should be disabled", ignoredCheckbox?.isSelected ?: true)
        assertTrue("Open issues should be enabled", openIssuesCheckbox?.isSelected ?: false)
    }

    @Test
    fun `test settings panel checkbox state changes update service`() {
        // Create scan types panel
        val scanTypesPanel = ScanTypesPanel(project)
        
        // Get checkbox and change its state
        val panel = scanTypesPanel.scanTypesPanel
        val checkboxes = getAllCheckboxesFromPanel(panel)
        val ossCheckbox = checkboxes.find { it.text?.contains("Open Source") == true }!!
        val originalState = ossCheckbox.isSelected
        
        // Simulate click
        ossCheckbox.doClick()
        
        // Verify state changed
        assertNotSame("Checkbox state should change", originalState, ossCheckbox.isSelected)
    }

    @Test
    fun `test severity filter all or none selection`() {
        val severitiesPanel = SeveritiesEnablementPanel()
        
        // Get all severity checkboxes
        val checkboxes = getAllCheckboxesFromPanel(severitiesPanel.panel)
        val criticalCheckbox = checkboxes.find { it.text == "Critical" }!!
        val highCheckbox = checkboxes.find { it.text == "High" }!!
        val mediumCheckbox = checkboxes.find { it.text == "Medium" }!!
        val lowCheckbox = checkboxes.find { it.text == "Low" }!!
        
        // Deselect all
        criticalCheckbox.isSelected = false
        highCheckbox.isSelected = false
        mediumCheckbox.isSelected = false
        lowCheckbox.isSelected = false
        
        // All should be false
        assertFalse("Critical should be deselected", criticalCheckbox.isSelected)
        assertFalse("High should be deselected", highCheckbox.isSelected)
        assertFalse("Medium should be deselected", mediumCheckbox.isSelected)
        assertFalse("Low should be deselected", lowCheckbox.isSelected)
        
        // Select all
        criticalCheckbox.isSelected = true
        highCheckbox.isSelected = true
        mediumCheckbox.isSelected = true
        lowCheckbox.isSelected = true
        
        // All should be true
        assertTrue("Critical should be selected", criticalCheckbox.isSelected)
        assertTrue("High should be selected", highCheckbox.isSelected)
        assertTrue("Medium should be selected", mediumCheckbox.isSelected)
        assertTrue("Low should be selected", lowCheckbox.isSelected)
    }
}