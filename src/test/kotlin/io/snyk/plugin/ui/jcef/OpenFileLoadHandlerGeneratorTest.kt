package io.snyk.plugin.ui.jcef

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.mockk.unmockkAll
import io.snyk.plugin.resetSettings
import io.snyk.plugin.snykcode.core.SnykCodeFile
import org.junit.Test
import snyk.code.annotator.SnykCodeAnnotator
import java.nio.file.Paths

class OpenFileLoadHandlerGeneratorTest : BasePlatformTestCase(){
    private lateinit var generator: OpenFileLoadHandlerGenerator
    private val fileName = "app.js"
    private lateinit var file: VirtualFile
    private lateinit var psiFile: PsiFile
    private lateinit var snykCodeFile: SnykCodeFile

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
        snykCodeFile = SnykCodeFile(psiFile.project, psiFile.virtualFile)

        generator = OpenFileLoadHandlerGenerator(snykCodeFile)
    }

    @Test
    fun `test openFile should navigate to source`() {
        val res = generator.openFile("$fileName:1:2:3:4")
        assertNotNull(res)
    }
}
