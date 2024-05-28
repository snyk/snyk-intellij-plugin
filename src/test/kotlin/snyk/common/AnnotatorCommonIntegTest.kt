package snyk.common

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.snyk.plugin.Severity
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.resetSettings
import org.junit.After
import org.junit.Before
import org.junit.Test

class AnnotatorCommonIntegTest : BasePlatformTestCase()  {

    override fun setUp() {
        super.setUp()
        resetSettings(project)
    }

    override fun tearDown() {
        resetSettings(project)
        super.tearDown()
    }

    @Test
    fun `test annotation filtered by severity settings`() {
        val control = AnnotatorCommon.isSeverityToShow(Severity.HIGH)
        assertTrue(control)

        pluginSettings().highSeverityEnabled = false
        val actual = AnnotatorCommon.isSeverityToShow(Severity.HIGH)
        assertFalse(actual)
    }
}
