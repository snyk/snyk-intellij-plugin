package io.snyk.plugin.analytics

import io.snyk.plugin.services.SnykApplicationSettingsStateService
import org.junit.Assert.assertEquals
import org.junit.Test
import snyk.advisor.AdvisorPackageManager
import snyk.analytics.AnalysisIsReady.AnalysisType
import snyk.analytics.HealthScoreIsClicked

class ItlyHelperTest {

    @Test
    fun `empty selected products if nothing configured`() {
        val settings = SnykApplicationSettingsStateService()
        settings.ossScanEnable = false
        settings.snykCodeSecurityIssuesScanEnable = false
        settings.snykCodeQualityIssuesScanEnable = false
        settings.iacScanEnabled = false
        settings.containerScanEnabled = false

        val actualProducts = getSelectedProducts(settings)

        assertEquals(0, actualProducts.size)
    }

    @Test
    fun `one selected product if only one configured`() {
        val settings = SnykApplicationSettingsStateService()
        settings.ossScanEnable = true
        settings.snykCodeSecurityIssuesScanEnable = false
        settings.snykCodeQualityIssuesScanEnable = false
        settings.iacScanEnabled = false
        settings.containerScanEnabled = false

        val actualProducts = getSelectedProducts(settings)

        assertEquals(1, actualProducts.size)
        assertEquals(AnalysisType.SNYK_OPEN_SOURCE.analysisType, actualProducts[0])
    }

    @Test
    fun `itly npm ecosystem if advisor package manager is npm`() {
        val npmPackageManager = AdvisorPackageManager.NPM

        val actualEcosystem = npmPackageManager.getEcosystem()

        assertEquals(HealthScoreIsClicked.Ecosystem.NPM, actualEcosystem)
    }

    @Test
    fun `itly python ecosystem if advisor package manager is python`() {
        val pythonPackageManager = AdvisorPackageManager.PYTHON

        val actualEcosystem = pythonPackageManager.getEcosystem()

        assertEquals(HealthScoreIsClicked.Ecosystem.PYTHON, actualEcosystem)
    }
}
