package io.snyk.plugin.ui.toolwindow.settings

import UIComponentFinder.getComponentByName
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
import io.snyk.plugin.isSnykCodeAvailable
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.resetSettings
import io.snyk.plugin.ui.settings.ScanTypesPanel
import org.junit.Test
import snyk.container.KubernetesImageCache

@Suppress("FunctionName")
class ScanTypesPanelTest : LightPlatform4TestCase() {
    private var cacheMock: KubernetesImageCache = mockk()

    private lateinit var disposable: Disposable

    override fun setUp() {
        super.setUp()
        unmockkAll()
        resetSettings(project)
        disposable = Disposer.newDisposable()
        setUpContainerScanTypePanelTests()
    }

    override fun tearDown() {
        try {
            unmockkAll()
            resetSettings(project)
            disposable.dispose()
        } finally {
            super.tearDown()
        }
    }

    private fun setUpContainerScanTypePanelTests() {
        mockkStatic("io.snyk.plugin.UtilsKt")
        cacheMock = mockk(relaxed = true)
        every { isSnykCodeAvailable(any()) } returns false
        every { getKubernetesImageCache(project) } returns cacheMock
    }

    private fun getContainerCheckBox(
        initialValue: Boolean,
        switchSelection: Boolean
    ): JBCheckBox {
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
        justRun { cacheMock.scanProjectForKubernetesFiles() }

        getContainerCheckBox(initialValue = false, switchSelection = true)

        verify(exactly = 1) { cacheMock.scanProjectForKubernetesFiles() }
    }

    @Test
    fun `KubernetesImageCache clean up after container disablement`() {
        justRun { cacheMock.clear() }

        getContainerCheckBox(initialValue = true, switchSelection = true)

        verify(exactly = 1) { cacheMock.clear() }
    }
}
