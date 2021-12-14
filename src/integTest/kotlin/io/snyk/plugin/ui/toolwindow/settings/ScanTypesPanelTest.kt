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

    private fun getContainerCheckBox(
        initialValue: Boolean,
        switchSelection: Boolean
    ): JBCheckBox {
        setUpContainerTest()
        pluginSettings().containerScanEnabled = initialValue
        val scanTypesPanel = ScanTypesPanel(project, disposable)
        val containerCheckBox =
            getComponentByName(scanTypesPanel.panel, JBCheckBox::class, "containerEnablementCheckBox")
                ?: throw IllegalStateException("containerEnablementCheckBox not found")
        if (switchSelection) {
            containerCheckBox.doClick()
            scanTypesPanel.panel.apply()
        }
        return containerCheckBox
    }

    @Test
    fun `container scan enablement get from settings`() {
        val containerCheckBox = getContainerCheckBox(initialValue = true, switchSelection = false)

        assertTrue(
            "Expected container checkbox `selected` state to be gotten from setting, but wasn't",
            containerCheckBox.isSelected
        )
    }

    @Test
    fun `container scan enablement set to settings`() {
        getContainerCheckBox(initialValue = true, switchSelection = true)

        assertFalse(
            "Expected container checkbox `deselected` state to be written to setting, but wasn't",
            pluginSettings().containerScanEnabled
        )
    }

    @Test
    fun `container scan disablement get from settings`() {
        val containerCheckBox = getContainerCheckBox(initialValue = false, switchSelection = false)

        assertFalse(
            "Expected container checkbox `deselected` state to be gotten from setting, but wasn't",
            containerCheckBox.isSelected)
    }

    @Test
    fun `container scan disablement set to settings`() {
        getContainerCheckBox(initialValue = false, switchSelection = true)

        assertTrue(
            "Expected container checkbox `selected` state to be written to setting, but wasn't",
            pluginSettings().containerScanEnabled
        )
    }

    @Test
    fun `KubernetesImageCache rescan after container enablement`() {
        mockkStatic("io.snyk.plugin.UtilsKt")
        val spyk = spyk(getKubernetesImageCache(project))
        every { getKubernetesImageCache(project) } returns spyk
        getContainerCheckBox(initialValue = false, switchSelection = true)

        verify(exactly = 1) { spyk.scanProjectForKubernetesFiles() }
    }

    @Test
    fun `KubernetesImageCache clean up after container disablement`() {
        mockkStatic("io.snyk.plugin.UtilsKt")
        val spyk = spyk(getKubernetesImageCache(project))
        every { getKubernetesImageCache(project) } returns spyk
        getContainerCheckBox(initialValue = true, switchSelection = true)

        verify(exactly = 1) { spyk.clear() }
    }
}
