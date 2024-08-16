package io.snyk.plugin.ui.jcef

import com.intellij.execution.testframework.TestsUIUtil
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.concurrency.AppExecutorUtil
import com.jetbrains.rd.util.threading.coroutines.waitFor
import io.mockk.unmockkAll
import io.snyk.plugin.resetSettings
import junit.framework.TestCase
import org.awaitility.Awaitility
import org.junit.Test
import snyk.code.annotator.SnykCodeAnnotator
import java.nio.file.Paths
import java.time.Duration
import java.time.temporal.TemporalUnit
import java.util.concurrent.TimeUnit
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

    @Test
    fun `test openFile should navigate to source`() {
        val res = generator.openFile("$fileName:1:2:3:4")
        val matcher = BooleanSupplier { FileEditorManager.getInstance(project).isFileOpen(psiFile.virtualFile) }
        PlatformTestUtil.waitWithEventsDispatching("navigate was not called", matcher, 10)
    }
}
