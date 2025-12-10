package io.snyk.plugin.ui.jcef

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.mockk.unmockkAll
import io.snyk.plugin.resetSettings
import snyk.common.annotator.SnykCodeAnnotator
import java.nio.file.Paths
import java.util.function.BooleanSupplier

class OpenFileLoadHandlerGeneratorTest : BasePlatformTestCase() {
    private lateinit var generator: OpenFileLoadHandlerGenerator
    private val fileName = "app.js"
    private lateinit var file: VirtualFile
    private lateinit var psiFile: PsiFile

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
    }

    fun `test openFile should navigate to source`() {
        generator.openFile("$fileName|1|2|3|4")
        val matcher = BooleanSupplier { FileEditorManager.getInstance(project).isFileOpen(psiFile.virtualFile) }
        PlatformTestUtil.waitWithEventsDispatching("navigate was not called", matcher, 10)
    }

    fun `test openFile should use pipe separator instead of colon`() {
        // This tests that the old format with colons would fail
        try {
            generator.openFile("$fileName:1:2:3:4")
            fail("Should throw exception for old colon format")
        } catch (e: IndexOutOfBoundsException) {
            // Expected behavior - old format with colons should fail
        }
    }

    fun `test openFile should handle Windows-style paths with colons`() {
        val windowsPath = "C:/Users/test/app.js"
        val virtualFiles = LinkedHashMap<String, VirtualFile?>()
        virtualFiles[windowsPath] = psiFile.virtualFile

        val windowsGenerator = OpenFileLoadHandlerGenerator(project, virtualFiles)
        val response = windowsGenerator.openFile("$windowsPath|10|15|5|20")

        assertNotNull(response)
        assertEquals("success", response.response())
    }

    fun `test openFile should handle file paths with multiple colons`() {
        val complexPath = "file://localhost:8080/test/app.js"
        val virtualFiles = LinkedHashMap<String, VirtualFile?>()
        virtualFiles[complexPath] = psiFile.virtualFile

        val complexGenerator = OpenFileLoadHandlerGenerator(project, virtualFiles)
        val response = complexGenerator.openFile("$complexPath|1|2|3|4")

        assertNotNull(response)
        assertEquals("success", response.response())

        // Ensure async navigation completes before test finishes.
        val matcher = BooleanSupplier { FileEditorManager.getInstance(project).isFileOpen(psiFile.virtualFile) }
        PlatformTestUtil.waitWithEventsDispatching("navigate was not called", matcher, 10)
    }

    fun `test openFile should return success when file not found in virtualFiles`() {
        val response = generator.openFile("nonexistent.js|1|2|3|4")
        assertNotNull(response)
        assertEquals("success", response.response())
    }

    fun `test openFile should handle newlines in input`() {
        val inputWithNewlines = "$fileName|1|2|3|4\n"
        val response = generator.openFile(inputWithNewlines)

        assertNotNull(response)
        assertEquals("success", response.response())
        val matcher = BooleanSupplier { FileEditorManager.getInstance(project).isFileOpen(psiFile.virtualFile) }
        PlatformTestUtil.waitWithEventsDispatching("navigate was not called", matcher, 10)
    }

    fun `test openFile should handle navigation with large line numbers`() {
        // Test with line numbers beyond file bounds - should handle gracefully
        val response = generator.openFile("$fileName|999|1000|50|100")
        assertNotNull(response)
        assertEquals("success", response.response())
        // Navigation should still succeed even with out-of-bounds line numbers
    }

    fun `test navigationSeparator constant should be pipe character`() {
        assertEquals("|", navigationSeparator)
    }

    fun `test openFile should handle zero-based indices`() {
        val response = generator.openFile("$fileName|0|0|0|0")
        assertNotNull(response)
        assertEquals("success", response.response())
    }

    fun `test openFile should handle malformed input gracefully`() {
        try {
            // Missing parameters
            generator.openFile("$fileName|1|2")
            fail("Should throw exception for malformed input")
        } catch (e: IndexOutOfBoundsException) {
            // Expected behavior
        }
    }

    fun `test openFile should handle non-numeric parameters`() {
        try {
            generator.openFile("$fileName|abc|def|ghi|jkl")
            fail("Should throw exception for non-numeric parameters")
        } catch (e: NumberFormatException) {
            // Expected behavior
        }
    }
}
