package io.snyk.plugin.ui.jcef

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.jcef.JBCefBrowserBase
import io.mockk.*
import io.snyk.plugin.resetSettings
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLoadHandlerAdapter
import snyk.common.annotator.SnykCodeAnnotator
import java.nio.file.Paths

class OpenFileLoadHandlerGeneratorIntegrationTest : BasePlatformTestCase() {
    private lateinit var generator: OpenFileLoadHandlerGenerator
    private val fileName = "app.js"
    private lateinit var file: VirtualFile
    private lateinit var psiFile: PsiFile
    private lateinit var mockBrowser: JBCefBrowserBase
    private lateinit var mockCefBrowser: CefBrowser
    private lateinit var mockCefFrame: CefFrame

    override fun getTestDataPath(): String {
        val resource = SnykCodeAnnotator::class.java.getResource("/test-fixtures/code/annotator")
        requireNotNull(resource) { "Make sure that the resource $resource exists!" }
        return Paths.get(resource.toURI()).toString()
    }

    override fun setUp() {
        super.setUp()
        unmockkAll()
        resetSettings(project)

        file = myFixture.copyFileToProject(fileName)
        psiFile = WriteAction.computeAndWait<PsiFile, Throwable> { psiManager.findFile(file)!! }

        val virtualFiles = LinkedHashMap<String, VirtualFile?>()
        virtualFiles[fileName] = psiFile.virtualFile

        generator = OpenFileLoadHandlerGenerator(psiFile.project, virtualFiles)
        
        // Mock JBCef components
        mockBrowser = mockk(relaxed = true)
        mockCefBrowser = mockk(relaxed = true)
        mockCefFrame = mockk(relaxed = true)
    }

    override fun tearDown() {
        unmockkAll()
        super.tearDown()
    }

    fun `test generated JavaScript should use navigationSeparator constant`() {
        every { mockCefFrame.isMain } returns true
        every { mockCefBrowser.url } returns "https://test.com"
        
        val loadHandler = generator.generate(mockBrowser)
        loadHandler.onLoadEnd(mockCefBrowser, mockCefFrame, 200)
        
        verify {
            mockCefBrowser.executeJavaScript(
                match { script ->
                    script.contains("$navigationSeparator") &&
                    script.contains("getAttribute(\"file-path\") + \"$navigationSeparator\"") &&
                    script.contains("getAttribute(\"start-line\") + \"$navigationSeparator\"") &&
                    script.contains("getAttribute(\"end-line\") + \"$navigationSeparator\"") &&
                    script.contains("getAttribute(\"start-character\") + \"$navigationSeparator\"")
                },
                any(),
                0
            )
        }
    }

    fun `test generated JavaScript should handle data-flow-clickable-row elements`() {
        every { mockCefFrame.isMain } returns true
        every { mockCefBrowser.url } returns "https://test.com"
        
        val loadHandler = generator.generate(mockBrowser)
        loadHandler.onLoadEnd(mockCefBrowser, mockCefFrame, 200)
        
        verify {
            mockCefBrowser.executeJavaScript(
                match { script ->
                    script.contains("getElementsByClassName('data-flow-clickable-row')") &&
                    script.contains("addEventListener('click'")
                },
                any(),
                0
            )
        }
    }

    fun `test generated JavaScript should handle position-line element`() {
        every { mockCefFrame.isMain } returns true
        every { mockCefBrowser.url } returns "https://test.com"
        
        val loadHandler = generator.generate(mockBrowser)
        loadHandler.onLoadEnd(mockCefBrowser, mockCefFrame, 200)
        
        verify {
            mockCefBrowser.executeJavaScript(
                match { script ->
                    script.contains("getElementById('position-line')") &&
                    script.contains("addEventListener('click'") &&
                    script.contains("getElementsByClassName('data-flow-clickable-row')[0]")
                },
                any(),
                0
            )
        }
    }

    fun `test loadHandler should only execute JavaScript on main frame`() {
        every { mockCefFrame.isMain } returns false
        
        val loadHandler = generator.generate(mockBrowser)
        loadHandler.onLoadEnd(mockCefBrowser, mockCefFrame, 200)
        
        verify(exactly = 0) {
            mockCefBrowser.executeJavaScript(any(), any(), any())
        }
    }

    fun `test generated JavaScript should prevent default event behavior`() {
        every { mockCefFrame.isMain } returns true
        every { mockCefBrowser.url } returns "https://test.com"
        
        val loadHandler = generator.generate(mockBrowser)
        loadHandler.onLoadEnd(mockCefBrowser, mockCefFrame, 200)
        
        verify {
            mockCefBrowser.executeJavaScript(
                match { script ->
                    script.contains("e.preventDefault()")
                },
                any(),
                0
            )
        }
    }

    fun `test generated JavaScript should check for existing openFileQuery`() {
        every { mockCefFrame.isMain } returns true
        every { mockCefBrowser.url } returns "https://test.com"
        
        val loadHandler = generator.generate(mockBrowser)
        loadHandler.onLoadEnd(mockCefBrowser, mockCefFrame, 200)
        
        verify {
            mockCefBrowser.executeJavaScript(
                match { script ->
                    script.contains("if (window.openFileQuery)") &&
                    script.contains("return;")
                },
                any(),
                0
            )
        }
    }

    fun `test loadHandler type should be CefLoadHandlerAdapter`() {
        val loadHandler = generator.generate(mockBrowser)
        assertTrue(loadHandler is CefLoadHandlerAdapter)
    }

    fun `test JavaScript injection should include all required attributes`() {
        every { mockCefFrame.isMain } returns true
        every { mockCefBrowser.url } returns "https://test.com"
        
        val loadHandler = generator.generate(mockBrowser)
        loadHandler.onLoadEnd(mockCefBrowser, mockCefFrame, 200)
        
        val requiredAttributes = listOf(
            "file-path",
            "start-line",
            "end-line",
            "start-character",
            "end-character"
        )
        
        verify {
            mockCefBrowser.executeJavaScript(
                match { script ->
                    requiredAttributes.all { attr ->
                        script.contains("getAttribute(\"$attr\")")
                    }
                },
                any(),
                0
            )
        }
    }
} 