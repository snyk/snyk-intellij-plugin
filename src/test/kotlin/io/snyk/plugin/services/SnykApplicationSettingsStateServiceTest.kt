package io.snyk.plugin.services

import io.snyk.plugin.Severity
import junit.framework.TestCase.*
import org.junit.Test

class SnykApplicationSettingsStateServiceTest {

    @Test
    fun hasSeverityEnabled_allSeveritiesEnabledByDefault() {
        val target = SnykApplicationSettingsStateService()

        assertTrue(target.hasSeverityEnabled(Severity.CRITICAL))
        assertTrue(target.hasSeverityEnabled(Severity.HIGH))
        assertTrue(target.hasSeverityEnabled(Severity.MEDIUM))
        assertTrue(target.hasSeverityEnabled(Severity.LOW))
    }

    @Test
    fun hasSeverityEnabled_someDisabled() {
        val target = SnykApplicationSettingsStateService()
        target.lowSeverityEnabled = false
        target.criticalSeverityEnabled = false

        assertFalse(target.hasSeverityEnabled(Severity.CRITICAL))
        assertFalse(target.hasSeverityEnabled(Severity.LOW))
    }
}
