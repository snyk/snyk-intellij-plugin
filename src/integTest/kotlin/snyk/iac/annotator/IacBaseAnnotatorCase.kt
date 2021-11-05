package snyk.iac.annotator

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.replaceService
import io.mockk.clearAllMocks
import io.mockk.mockk
import io.snyk.plugin.ui.toolwindow.SnykToolWindowPanel
import org.junit.Before
import java.nio.file.Paths

abstract class IacBaseAnnotatorCase : BasePlatformTestCase() {

    val toolWindowPanel: SnykToolWindowPanel = mockk(relaxed = true)

    override fun getTestDataPath(): String {
        val resource = IacBaseAnnotatorCase::class.java.getResource("/test-fixtures/iac/annotator")
        requireNotNull(resource) { "Make sure that the resource $resource exists!" }
        return Paths.get(resource.toURI()).toString()
    }

    override fun isWriteActionRequired(): Boolean = true

    @Before
    override fun setUp() {
        super.setUp()
        project.replaceService(SnykToolWindowPanel::class.java, toolWindowPanel, project)
        clearAllMocks()
    }
}
