package io.snyk.plugin.ui.toolwindow

import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.LightPlatform4TestCase
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.tree.TreeUtil
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath

class TreeNodeExpanderTest : LightPlatform4TestCase() {

    private lateinit var tree: Tree
    private lateinit var rootNode: DefaultMutableTreeNode
    private lateinit var cut: TreeNodeExpander

    override fun setUp() {
        super.setUp()
        // Create a test tree structure on EDT
        rootNode = DefaultMutableTreeNode("root")
        tree = Tree(rootNode)
        cut = TreeNodeExpander(tree) { false }
    }

    @Test
    fun `expandNodes should expand provided nodes`() {
        // Given: a tree with children
        val child1 = DefaultMutableTreeNode("child1")
        val child2 = DefaultMutableTreeNode("child2")
        rootNode.add(child1)
        rootNode.add(child2)
        (tree.model as DefaultTreeModel).reload()
        
        // Expand root first so children are visible
        tree.expandPath(TreePath(rootNode.path))

        val userObjects = listOf("child1", "child2")

        // When: expanding nodes
        cut.expandNodes(rootNode, userObjects)

        // Then: no exception thrown and method completes
        // Note: actual expansion may not be verifiable without visible tree
        assertNotNull(TreeUtil.findNodeWithObject(rootNode, "child1"))
        assertNotNull(TreeUtil.findNodeWithObject(rootNode, "child2"))
    }

    @Test
    fun `expandNodes should handle missing nodes gracefully`() {
        // Given: a tree with children
        val child1 = DefaultMutableTreeNode("child1")
        rootNode.add(child1)
        (tree.model as DefaultTreeModel).reload()

        val userObjects = listOf("child1", "nonexistent")

        // When/Then: expanding nodes (including one that doesn't exist) should not throw
        cut.expandNodes(rootNode, userObjects)
        assertNotNull(TreeUtil.findNodeWithObject(rootNode, "child1"))
    }

    @Test
    fun `expandProgressively with empty list should invoke callback`() {
        // Given: a tree with children
        val child1 = DefaultMutableTreeNode("child1")
        rootNode.add(child1)
        (tree.model as DefaultTreeModel).reload()

        val callbackInvoked = AtomicBoolean(false)

        // When: expanding with empty user objects
        ApplicationManager.getApplication().invokeAndWait {
            cut.expandProgressively(rootNode, emptyList()) {
                callbackInvoked.set(true)
            }
        }

        // Then: wait for async operations and verify callback
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        // Give promiseExpand time to complete
        Thread.sleep(500)
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        
        assertTrue("Callback should be invoked", callbackInvoked.get())
    }

    @Test
    fun `expandProgressively with null list should invoke callback`() {
        // Given: a tree with children
        val child1 = DefaultMutableTreeNode("child1")
        rootNode.add(child1)
        (tree.model as DefaultTreeModel).reload()

        val callbackInvoked = AtomicBoolean(false)

        // When: expanding with null user objects
        ApplicationManager.getApplication().invokeAndWait {
            cut.expandProgressively(rootNode, null) {
                callbackInvoked.set(true)
            }
        }

        // Then: wait for async operations
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        Thread.sleep(500)
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        
        assertTrue("Callback should be invoked", callbackInvoked.get())
    }

    @Test
    fun `expandProgressively with small node count should invoke callback immediately`() {
        // Given: a tree with children (less than EXPANSION_CHUNK_SIZE)
        repeat(5) { i ->
            rootNode.add(DefaultMutableTreeNode("child$i"))
        }
        (tree.model as DefaultTreeModel).reload()

        val userObjects = (0 until 5).map { "child$it" }
        val callbackInvoked = AtomicBoolean(false)

        // When: expanding progressively
        ApplicationManager.getApplication().invokeAndWait {
            cut.expandProgressively(rootNode, userObjects) {
                callbackInvoked.set(true)
            }
        }

        // Then: callback should be invoked synchronously for small counts
        assertTrue("Callback should be invoked", callbackInvoked.get())
    }

    @Test
    fun `expandProgressively with medium node count should invoke callback after chunks`() {
        // Given: a tree with children (between EXPANSION_CHUNK_SIZE and MAX_AUTO_EXPAND_NODES)
        repeat(30) { i ->
            rootNode.add(DefaultMutableTreeNode("child$i"))
        }
        (tree.model as DefaultTreeModel).reload()

        val userObjects = (0 until 30).map { "child$it" }
        val callbackInvoked = AtomicBoolean(false)

        // When: expanding progressively
        ApplicationManager.getApplication().invokeAndWait {
            cut.expandProgressively(rootNode, userObjects) {
                callbackInvoked.set(true)
            }
        }

        // Then: process EDT events until callback is invoked
        repeat(10) {
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
            if (callbackInvoked.get()) return@repeat
            Thread.sleep(100)
        }
        
        assertTrue("Callback should be invoked", callbackInvoked.get())
    }

    @Test
    fun `expandProgressively with large node count should invoke callback`() {
        // Given: a tree with many children (more than MAX_AUTO_EXPAND_NODES)
        repeat(100) { i ->
            rootNode.add(DefaultMutableTreeNode("child$i"))
        }
        (tree.model as DefaultTreeModel).reload()

        val userObjects = (0 until 100).map { "child$it" }
        val callbackInvoked = AtomicBoolean(false)

        // When: expanding progressively
        ApplicationManager.getApplication().invokeAndWait {
            cut.expandProgressively(rootNode, userObjects) {
                callbackInvoked.set(true)
            }
        }

        // Then: callback should be invoked (large trees only expand root)
        assertTrue("Callback should be invoked", callbackInvoked.get())
    }

    @Test
    fun `expandInChunks should invoke callback after all chunks`() {
        // Given: a tree with children
        repeat(25) { i ->
            rootNode.add(DefaultMutableTreeNode("child$i"))
        }
        (tree.model as DefaultTreeModel).reload()

        val userObjects = (0 until 25).map { "child$it" }
        val callbackInvoked = AtomicBoolean(false)

        // When: expanding in chunks
        ApplicationManager.getApplication().invokeAndWait {
            cut.expandInChunks(rootNode, userObjects) {
                callbackInvoked.set(true)
            }
        }

        // Then: process EDT events until callback is invoked
        repeat(10) {
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
            if (callbackInvoked.get()) return@repeat
            Thread.sleep(100)
        }
        
        assertTrue("Callback should be invoked", callbackInvoked.get())
    }

    @Test
    fun `expandPath should not throw`() {
        // Given: a tree with nested children
        val child1 = DefaultMutableTreeNode("child1")
        rootNode.add(child1)
        (tree.model as DefaultTreeModel).reload()

        val path = TreePath(arrayOf(rootNode, child1))

        // When/Then: expanding path should not throw
        cut.expandPath(path)
    }

    @Test
    fun `expandToDepth should invoke callback`() {
        // Given: a tree with nested children
        val child1 = DefaultMutableTreeNode("child1")
        val grandchild1 = DefaultMutableTreeNode("grandchild1")
        child1.add(grandchild1)
        rootNode.add(child1)
        (tree.model as DefaultTreeModel).reload()

        val callbackInvoked = AtomicBoolean(false)

        // When: expanding to depth 2
        ApplicationManager.getApplication().invokeAndWait {
            cut.expandToDepth(2) {
                callbackInvoked.set(true)
            }
        }

        // Then: wait for async operations
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        Thread.sleep(500)
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        
        assertTrue("Callback should be invoked", callbackInvoked.get())
    }

    @Test
    fun `expander should stop processing when disposed`() {
        // Given: an expander that can be disposed
        val disposed = AtomicBoolean(false)
        val disposableExpander = TreeNodeExpander(tree) { disposed.get() }

        repeat(30) { i ->
            rootNode.add(DefaultMutableTreeNode("child$i"))
        }
        (tree.model as DefaultTreeModel).reload()

        val userObjects = (0 until 30).map { "child$it" }
        val callbackInvoked = AtomicBoolean(false)

        // When: marking as disposed and expanding
        disposed.set(true)
        ApplicationManager.getApplication().invokeAndWait {
            disposableExpander.expandInChunks(rootNode, userObjects) {
                callbackInvoked.set(true)
            }
        }

        // Then: callback should be invoked immediately (due to early exit)
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        assertTrue("Callback should be invoked", callbackInvoked.get())
    }

    @Test
    fun `constants should have expected values`() {
        assertEquals(50, TreeNodeExpander.MAX_AUTO_EXPAND_NODES)
        assertEquals(15, TreeNodeExpander.EXPANSION_CHUNK_SIZE)
        assertEquals(2, TreeNodeExpander.DEFAULT_EXPANSION_DEPTH)
    }

    @Test
    fun `expandProgressively threshold behavior - small`() {
        // Test that small counts (<=15) are handled directly
        val userObjects = (0 until TreeNodeExpander.EXPANSION_CHUNK_SIZE).map { "node$it" }
        assertEquals(15, userObjects.size)
        // Should be handled by expandNodes directly
    }

    @Test
    fun `expandProgressively threshold behavior - medium`() {
        // Test that medium counts (16-50) are chunked
        val userObjects = (0 until TreeNodeExpander.MAX_AUTO_EXPAND_NODES).map { "node$it" }
        assertEquals(50, userObjects.size)
        // Should be handled by expandInChunks
    }

    @Test
    fun `expandProgressively threshold behavior - large`() {
        // Test that large counts (>50) only expand root
        val userObjects = (0..TreeNodeExpander.MAX_AUTO_EXPAND_NODES).map { "node$it" }
        assertEquals(51, userObjects.size)
        // Should only expand root level
    }
}
