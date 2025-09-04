package snyk.common

import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.ui.treeStructure.Tree
import io.mockk.every
import io.mockk.mockk
import org.eclipse.lsp4j.services.LanguageServer
import org.eclipse.lsp4j.services.WorkspaceService
import snyk.common.lsp.LanguageServerWrapper
import java.awt.Component
import java.awt.Container
import java.awt.event.MouseEvent
import java.util.concurrent.CompletableFuture
import javax.swing.JComponent
import javax.swing.tree.TreePath
import kotlin.reflect.KClass

/**
 * Enhanced UI testing utilities for Snyk IntelliJ plugin
 * Following existing patterns from UIComponentFinder and test base classes
 */
object UITestUtils {

    /**
     * Wait for a component to become available with timeout
     */
    fun <T : Component> waitForComponent(
        parent: Container,
        componentClass: KClass<T>,
        condition: (T) -> Boolean = { true },
        timeoutMillis: Long = 5000
    ): T? {
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < timeoutMillis) {
            val found = UIComponentFinder.getComponentByCondition(parent, componentClass, condition)
            if (found != null) {
                return found
            }
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
            Thread.sleep(50)
        }
        return null
    }

    /**
     * Simulate a click on a component
     */
    fun simulateClick(component: JComponent) {
        ApplicationManager.getApplication().invokeLater {
            val mouseEvent = MouseEvent(
                component,
                MouseEvent.MOUSE_CLICKED,
                System.currentTimeMillis(),
                0,
                component.width / 2,
                component.height / 2,
                1,
                false
            )
            component.dispatchEvent(mouseEvent)
        }
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    }

    /**
     * Simulate tree node selection
     */
    fun simulateTreeSelection(tree: Tree, path: TreePath) {
        ApplicationManager.getApplication().invokeLater {
            tree.selectionPath = path
        }
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    }

    /**
     * Create a mock LanguageServerWrapper with basic setup
     */
    fun createMockLanguageServerWrapper(): LanguageServerWrapper {
        val wrapper = mockk<LanguageServerWrapper>(relaxed = true)
        val languageServer = mockk<LanguageServer>(relaxed = true)
        val workspaceService = mockk<WorkspaceService>(relaxed = true)

        every { wrapper.isInitialized } returns true
        every { wrapper.languageServer } returns languageServer
        every { languageServer.workspaceService } returns workspaceService
        every { workspaceService.executeCommand(any()) } returns CompletableFuture.completedFuture(null)

        return wrapper
    }

    /**
     * Wait for UI updates to complete
     */
    fun waitForUiUpdates() {
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        Thread.sleep(100) // Small delay for async updates
    }

    /**
     * Check if a component is visible in the hierarchy
     */
    fun isComponentVisible(component: Component): Boolean {
        return component.isVisible && component.parent?.let { isComponentVisible(it) } ?: true
    }

    /**
     * Find all components of a specific type
     */
    fun <T : Component> findAllComponents(parent: Container, clazz: KClass<T>): List<T> {
        val result = mutableListOf<T>()
        findAllComponentsRecursive(parent, clazz, result)
        return result
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : Component> findAllComponentsRecursive(
        parent: Container,
        clazz: KClass<T>,
        result: MutableList<T>
    ) {
        for (component in parent.components) {
            if (clazz.isInstance(component)) {
                result.add(component as T)
            }
            if (component is Container) {
                findAllComponentsRecursive(component, clazz, result)
            }
        }
    }
}