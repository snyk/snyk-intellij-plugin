package io.snyk.plugin.ui.toolwindow

import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.ui.tree.TreeUtil
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreePath

/**
 * Handles progressive tree node expansion to avoid blocking the EDT.
 * Supports chunked expansion with EDT yields between chunks, and uses
 * IntelliJ's async promiseExpand API when appropriate.
 */
class TreeNodeExpander(
    private val tree: JTree,
    private val isDisposed: () -> Boolean = { false }
) {
    private val logger = Logger.getInstance(TreeNodeExpander::class.java)

    /**
     * Expands tree nodes progressively in chunks to avoid blocking the EDT.
     * For small node counts, expands immediately. For large counts, chunks the work.
     *
     * @param rootNode The root node to expand from
     * @param userObjects Previously expanded user objects to restore, or null for default expansion
     * @param onComplete Callback invoked when expansion is complete
     */
    fun expandProgressively(
        rootNode: DefaultMutableTreeNode,
        userObjects: List<Any>?,
        onComplete: () -> Unit
    ) {
        if (userObjects.isNullOrEmpty()) {
            // No previous expansion state - use promiseExpand for default expansion
            TreeUtil.promiseExpand(tree, DEFAULT_EXPANSION_DEPTH).onSuccess { onComplete() }
            return
        }

        val nodeCount = userObjects.size

        when {
            // Small tree: expand all at once (fast enough)
            nodeCount <= EXPANSION_CHUNK_SIZE -> {
                expandNodes(rootNode, userObjects)
                onComplete()
            }
            // Medium tree: expand in chunks
            nodeCount <= MAX_AUTO_EXPAND_NODES -> {
                expandInChunks(rootNode, userObjects, onComplete)
            }
            // Large tree: only expand first level to avoid EDT blocking
            else -> {
                logger.debug("Tree has $nodeCount expanded nodes - limiting auto-expansion")
                tree.expandPath(TreePath(rootNode.path))
                onComplete()
            }
        }
    }

    /**
     * Expands nodes immediately (for small node counts).
     */
    fun expandNodes(rootNode: DefaultMutableTreeNode, userObjects: List<Any>) {
        userObjects.forEach { userObject ->
            TreeUtil.findNodeWithObject(rootNode, userObject)?.path?.let { path ->
                tree.expandPath(TreePath(path))
            }
        }
    }

    /**
     * Expands nodes in chunks, yielding EDT between chunks to keep UI responsive.
     */
    fun expandInChunks(
        rootNode: DefaultMutableTreeNode,
        userObjects: List<Any>,
        onComplete: () -> Unit
    ) {
        val chunks = userObjects.chunked(EXPANSION_CHUNK_SIZE)
        expandChunkRecursively(rootNode, chunks, 0, onComplete)
    }

    /**
     * Recursively processes expansion chunks with EDT yields between each.
     */
    private fun expandChunkRecursively(
        rootNode: DefaultMutableTreeNode,
        chunks: List<List<Any>>,
        index: Int,
        onComplete: () -> Unit
    ) {
        if (isDisposed() || index >= chunks.size) {
            onComplete()
            return
        }

        // Process current chunk
        expandNodes(rootNode, chunks[index])

        // Yield EDT then process next chunk
        invokeLater {
            expandChunkRecursively(rootNode, chunks, index + 1, onComplete)
        }
    }

    /**
     * Expands a single path in the tree.
     */
    fun expandPath(path: TreePath) {
        tree.expandPath(path)
    }

    /**
     * Expands to a specific depth using IntelliJ's async API.
     */
    fun expandToDepth(depth: Int, onComplete: () -> Unit = {}) {
        TreeUtil.promiseExpand(tree, depth).onSuccess { onComplete() }
    }

    /**
     * Expands all children of a node recursively using chunked expansion.
     * Collects all paths first, then expands in chunks to avoid blocking EDT.
     *
     * @param node The node to expand recursively
     * @param onComplete Optional callback invoked when expansion is complete
     */
    fun expandRecursively(node: DefaultMutableTreeNode, onComplete: () -> Unit = {}) {
        val paths = mutableListOf<TreePath>()
        collectTreePaths(node, paths)

        if (paths.size <= EXPANSION_CHUNK_SIZE) {
            // Small tree: expand all at once
            invokeLater {
                paths.forEach { tree.expandPath(it) }
                onComplete()
            }
        } else {
            // Large tree: expand in chunks with EDT yields between them
            expandPathsInChunks(paths, 0, onComplete)
        }
    }

    private fun expandPathsInChunks(paths: List<TreePath>, startIndex: Int, onComplete: () -> Unit) {
        if (isDisposed() || startIndex >= paths.size) {
            onComplete()
            return
        }

        invokeLater {
            val endIndex = minOf(startIndex + EXPANSION_CHUNK_SIZE, paths.size)
            for (i in startIndex until endIndex) {
                tree.expandPath(paths[i])
            }
            if (endIndex < paths.size) {
                // Schedule next chunk, yielding EDT between chunks
                expandPathsInChunks(paths, endIndex, onComplete)
            } else {
                onComplete()
            }
        }
    }

    private fun collectTreePaths(node: DefaultMutableTreeNode, paths: MutableList<TreePath>) {
        paths.add(TreePath(node.path))
        node.children().asSequence().forEach {
            collectTreePaths(it as DefaultMutableTreeNode, paths)
        }
    }

    companion object {
        // Maximum nodes to auto-expand - prevents EDT blocking on large trees
        const val MAX_AUTO_EXPAND_NODES = 50

        // Chunk size for progressive node expansion
        const val EXPANSION_CHUNK_SIZE = 15

        // Default expansion depth when using promiseExpand
        const val DEFAULT_EXPANSION_DEPTH = 2

        /**
         * Static helper for expanding a node recursively without needing an instance.
         * Creates a temporary expander and performs chunked expansion.
         */
        fun expandNodeRecursively(tree: JTree, node: DefaultMutableTreeNode, onComplete: () -> Unit = {}) {
            val expander = TreeNodeExpander(tree)
            expander.expandRecursively(node, onComplete)
        }
    }
}
