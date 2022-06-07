package io.snyk.plugin.ui.toolwindow.settings

import snyk.common.UIComponentFinder.getComponentByName
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.LightPlatform4TestCase
import com.intellij.ui.components.JBCheckBox
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import io.snyk.plugin.getKubernetesImageCache
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.resetSettings
import io.snyk.plugin.ui.settings.ScanTypesPanel
import org.junit.Test
import snyk.common.ProductType
import snyk.common.isSnykCodeAvailable
import snyk.container.KubernetesImageCache

@Suppress("FunctionName")
class ScanTypesPanelTest : LightPlatform4TestCase() {
    private lateinit var disposable: Disposable

    override fun setUp() {
        super.setUp()
        unmockkAll()
        resetSettings(project)
        disposable = Disposer.newDisposable()
    }

    override fun tearDown() {
        unmockkAll()
        resetSettings(project)
        disposable.dispose()
        super.tearDown()
    }

    private fun setUpContainerScanTypePanelTests(): KubernetesImageCache {
        mockkStatic("io.snyk.plugin.UtilsKt")
        mockkStatic("snyk.common.CustomEndpointsKt")
        val cacheMock = mockk<KubernetesImageCache>(relaxed = true)
        every { isSnykCodeAvailable(any()) } returns false
        every { getKubernetesImageCache(project) } returns cacheMock
        return cacheMock
    }

    private fun getContainerCheckBox(
        initialValue: Boolean,
        switchSelection: Boolean
    ): JBCheckBox {
        pluginSettings().containerScanEnabled = initialValue
        val scanTypesPanel = ScanTypesPanel(project, disposable)
        val containerCheckBox =
            getComponentByName(scanTypesPanel.panel, JBCheckBox::class, ProductType.CONTAINER.toString())
                ?: throw IllegalStateException("containerEnablementCheckBox not found")
        if (switchSelection) {
            containerCheckBox.doClick()
            scanTypesPanel.panel.apply()
        }
        return containerCheckBox
    }

    @Test
    fun `container scan enablement get from settings`() {
        setUpContainerScanTypePanelTests()
        val containerCheckBox = getContainerCheckBox(initialValue = true, switchSelection = false)

        assertTrue(
            "Expected container checkbox `selected` state to be gotten from setting, but wasn't",
            containerCheckBox.isSelected
        )
    }

    @Test
    fun `container scan enablement set to settings`() {
        setUpContainerScanTypePanelTests()
        getContainerCheckBox(initialValue = true, switchSelection = true)

        assertFalse(
            "Expected container checkbox `deselected` state to be written to setting, but wasn't",
            pluginSettings().containerScanEnabled
        )
    }

    @Test
    fun `container scan disablement get from settings`() {
        setUpContainerScanTypePanelTests()
        val containerCheckBox = getContainerCheckBox(initialValue = false, switchSelection = false)

        assertFalse(
            "Expected container checkbox `deselected` state to be gotten from setting, but wasn't",
            containerCheckBox.isSelected
        )
    }

    @Test
    fun `container scan disablement set to settings`() {
        setUpContainerScanTypePanelTests()
        getContainerCheckBox(initialValue = false, switchSelection = true)

        assertTrue(
            "Expected container checkbox `selected` state to be written to setting, but wasn't",
            pluginSettings().containerScanEnabled
        )
    }

    @Test
    fun `KubernetesImageCache rescan after container enablement`() {
        val cacheMock = setUpContainerScanTypePanelTests()
        justRun { cacheMock.scanProjectForKubernetesFiles() }

        getContainerCheckBox(initialValue = false, switchSelection = true)

        verify(exactly = 1) { cacheMock.scanProjectForKubernetesFiles() }
    }

    @Test
    fun `KubernetesImageCache clean up after container disablement`() {
        val cacheMock = setUpContainerScanTypePanelTests()
        justRun { cacheMock.clear() }

        getContainerCheckBox(initialValue = true, switchSelection = true)

        verify(exactly = 1) { cacheMock.clear() }
    }
}
