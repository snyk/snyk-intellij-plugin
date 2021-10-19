package snyk.tree

import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.util.treeView.NodeRenderer
import com.intellij.navigation.ItemPresentation
import com.intellij.ui.SimpleTextAttributes
import java.awt.Color
import javax.swing.JTree

class BaseIssueNodeRenderer : NodeRenderer() {
    override fun customizeCellRenderer(
        tree: JTree,
        value: Any?,
        selected: Boolean,
        expanded: Boolean,
        leaf: Boolean,
        row: Int,
        hasFocus: Boolean
    ) {
        // TODO: custom rendering of text, line and attributes depending on type
        super.customizeCellRenderer(tree, value, selected, expanded, leaf, row, hasFocus)
    }

    override fun getPresentation(node: Any?): ItemPresentation? {
        // TODO: detail panels here over presentations
        return super.getPresentation(node)
    }

    override fun getSimpleTextAttributes(
        presentation: PresentationData,
        color: Color?,
        node: Any
    ): SimpleTextAttributes {
        // TODO: simple attributes should replace our old custom node customization
        return super.getSimpleTextAttributes(presentation, color, node)
    }
}
