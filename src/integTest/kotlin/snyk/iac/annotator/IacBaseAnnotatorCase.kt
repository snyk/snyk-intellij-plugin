package snyk.iac.annotator

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.replaceService
import io.mockk.mockk
import io.mockk.unmockkAll
import io.snyk.plugin.pluginSettings
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
        unmockkAll()
        project.replaceService(SnykToolWindowPanel::class.java, toolWindowPanel, project)
        pluginSettings().fileListenerEnabled = false
    }

    override fun tearDown() {
        unmockkAll()
        project.replaceService(SnykToolWindowPanel::class.java, SnykToolWindowPanel(project), project)
        super.tearDown()
        pluginSettings().fileListenerEnabled = true
    }
}
