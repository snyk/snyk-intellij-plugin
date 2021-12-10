package io.snyk.plugin.ui.toolwindow.settings

import UIComponentFinder.getComponentByName
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.LightPlatform4TestCase
import com.intellij.ui.components.JBCheckBox
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.verify
import io.snyk.plugin.getKubernetesImageCache
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.resetSettings
import io.snyk.plugin.ui.settings.ScanTypesPanel
import org.junit.Test

@Suppress("FunctionName")
class ScanTypesPanelTest : LightPlatform4TestCase() {

    override fun setUp() {
        super.setUp()
        unmockkAll()
        resetSettings(project)
        disposable = Disposer.newDisposable()
        // restore modified Registry value
        isContainerEnabledRegistryValue.setValue(isContainerEnabledDefaultValue)
    }

    override fun tearDown() {
        unmockkAll()
        resetSettings(project)
        disposable.dispose()
        // restore modified Registry value
        isContainerEnabledRegistryValue.setValue(isContainerEnabledDefaultValue)
        super.tearDown()
    }

    private lateinit var disposable: Disposable

    private val isContainerEnabledRegistryValue = Registry.get("snyk.preview.container.enabled")
    private val isContainerEnabledDefaultValue: Boolean by lazy { isContainerEnabledRegistryValue.asBoolean() }

    private fun setUpContainerTest() {
        isContainerEnabledRegistryValue.setValue(true)
    }

    @Test
    fun `container scan enablement get from settings`() {
        setUpContainerTest()
        val settings = pluginSettings()
        settings.containerScanEnabled = true

        val scanTypesPanel = ScanTypesPanel(project, disposable)
        val containerCheckBox =
            getComponentByName(scanTypesPanel.panel, JBCheckBox::class, "containerEnablementCheckBox")
                ?: throw IllegalStateException("containerEnablementCheckBox not found")

        assertTrue("", containerCheckBox.isSelected)
    }

    @Test
    fun `container scan enablement set to settings`() {
        setUpContainerTest()
        val settings = pluginSettings()
        settings.containerScanEnabled = true
        val scanTypesPanel = ScanTypesPanel(project, disposable)
        val containerCheckBox =
            getComponentByName(scanTypesPanel.panel, JBCheckBox::class, "containerEnablementCheckBox")
                ?: throw IllegalStateException("containerEnablementCheckBox not found")

        containerCheckBox.doClick()
        scanTypesPanel.panel.apply()

        assertFalse("", settings.containerScanEnabled)
    }

    @Test
    fun `container scan disablement get from settings`() {
        setUpContainerTest()
        val settings = pluginSettings()
        settings.containerScanEnabled = false

        val scanTypesPanel = ScanTypesPanel(project, disposable)
        val containerCheckBox =
            getComponentByName(scanTypesPanel.panel, JBCheckBox::class, "containerEnablementCheckBox")
                ?: throw IllegalStateException("containerEnablementCheckBox not found")

        assertFalse("", containerCheckBox.isSelected)
    }

    @Test
    fun `container scan disablement set to settings`() {
        setUpContainerTest()
        val settings = pluginSettings()
        settings.containerScanEnabled = false
        val scanTypesPanel = ScanTypesPanel(project, disposable)
        val containerCheckBox =
            getComponentByName(scanTypesPanel.panel, JBCheckBox::class, "containerEnablementCheckBox")
                ?: throw IllegalStateException("containerEnablementCheckBox not found")

        containerCheckBox.doClick()
        scanTypesPanel.panel.apply()

        assertTrue("", settings.containerScanEnabled)
    }

    @Test
    fun `KubernetesImageCache rescan after container enablement`() {
        setUpContainerTest()
        val settings = pluginSettings()
        settings.containerScanEnabled = false
        mockkStatic("io.snyk.plugin.UtilsKt")
        val spyk = spyk(getKubernetesImageCache(project))
        every { getKubernetesImageCache(project) } returns spyk
        val scanTypesPanel = ScanTypesPanel(project, disposable)
        val containerCheckBox =
            getComponentByName(scanTypesPanel.panel, JBCheckBox::class, "containerEnablementCheckBox")
                ?: throw IllegalStateException("containerEnablementCheckBox not found")

        containerCheckBox.doClick()
        scanTypesPanel.panel.apply()

        verify(exactly = 1) { spyk.scanProjectForKubernetesFiles() }
    }

    @Test
    fun `KubernetesImageCache clean up after container disablement`() {
        setUpContainerTest()
        val settings = pluginSettings()
        settings.containerScanEnabled = true
        mockkStatic("io.snyk.plugin.UtilsKt")
        val spyk = spyk(getKubernetesImageCache(project))
        every { getKubernetesImageCache(project) } returns spyk
        val scanTypesPanel = ScanTypesPanel(project, disposable)
        val containerCheckBox =
            getComponentByName(scanTypesPanel.panel, JBCheckBox::class, "containerEnablementCheckBox")
                ?: throw IllegalStateException("containerEnablementCheckBox not found")

        containerCheckBox.doClick()
        scanTypesPanel.panel.apply()

        verify(exactly = 1) { spyk.clear() }
    }
}
