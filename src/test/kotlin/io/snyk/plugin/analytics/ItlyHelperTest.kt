package io.snyk.plugin.analytics

import io.snyk.plugin.services.SnykApplicationSettingsStateService
import org.hamcrest.core.IsEqual.equalTo
import org.junit.Assert.assertThat
import org.junit.Test
import snyk.advisor.AdvisorPackageManager
import snyk.analytics.AnalysisIsReady.AnalysisType
import snyk.analytics.HealthScoreIsClicked

class ItlyHelperTest {

    @Test
    fun `empty selected products if nothing configured`() {
        val settings = SnykApplicationSettingsStateService()
        settings.cliScanEnable = false
        settings.snykCodeSecurityIssuesScanEnable = false
        settings.snykCodeQualityIssuesScanEnable = false

        val actualProducts = getSelectedProducts(settings)

        assertThat(actualProducts.size, equalTo(0))
    }

    @Test
    fun `one selected product if only one configured`() {
        val settings = SnykApplicationSettingsStateService()
        settings.cliScanEnable = true
        settings.snykCodeSecurityIssuesScanEnable = false
        settings.snykCodeQualityIssuesScanEnable = false

        val actualProducts = getSelectedProducts(settings)

        assertThat(actualProducts.size, equalTo(1))
        assertThat(actualProducts[0], equalTo(AnalysisType.SNYK_OPEN_SOURCE.analysisType))
    }

    @Test
    fun `itly npm ecosystem if advisor package manager is npm`() {
        val npmPackageManager = AdvisorPackageManager.NPM

        val actualEcosystem = npmPackageManager.getEcosystem()

        assertThat(actualEcosystem, equalTo(HealthScoreIsClicked.Ecosystem.NPM))
    }

    @Test
    fun `itly python ecosystem if advisor package manager is python`() {
        val pythonPackageManager = AdvisorPackageManager.PYTHON

        val actualEcosystem = pythonPackageManager.getEcosystem()

        assertThat(actualEcosystem, equalTo(HealthScoreIsClicked.Ecosystem.PYTHON))
    }
}
