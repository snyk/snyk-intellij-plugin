package io.snyk.plugin.ui

import io.snyk.plugin.model.DisplayNode
import javax.swing.event.TreeModelListener
import javax.swing.tree.{TreeModel, TreePath}
import monix.execution.atomic.Atomic


class DisplayNodeTreeModel(node: Atomic[DisplayNode]) extends TreeModel {
  override def getRoot: AnyRef = node.get

  override def getChild(parent: AnyRef, index: Int): AnyRef = parent match {
    case dn: DisplayNode => dn.nested(index)
    case _ => null
  }

  override def getChildCount(parent: AnyRef): Int = parent match {
    case dn: DisplayNode => dn.nested.size
    case _ => 0
  }

  override def isLeaf(node: AnyRef): Boolean = node match {
    case dn: DisplayNode => dn.nested.isEmpty
    case _ => true
  }

  override def valueForPathChanged(path: TreePath, newValue: AnyRef): Unit = {
    sys.error("Snyk Dependency Tree should NEVER be editable")
  }

  override def getIndexOfChild(parent: AnyRef, child: AnyRef): Int = (parent, child) match {
    case (p: DisplayNode, c: DisplayNode) => p.nested.indexOf(c)
    case _ => 0
  }

  override def addTreeModelListener(l: TreeModelListener): Unit = () //do nothing

  override def removeTreeModelListener(l: TreeModelListener): Unit = () //do nothing
}
