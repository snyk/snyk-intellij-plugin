package io.snyk.plugin.depsource.externalproject

import com.intellij.openapi.externalSystem.model.{DataNode => ModelDataNode}
import scala.collection.JavaConverters._
import scala.collection.breakOut

case class SDataNode[+T](data: T, children: Seq[SDataNode[Any]])

object SDataNode {
  def fromModelDataNode[T](node: ModelDataNode[T]): SDataNode[T] = {
    val children: Seq[SDataNode[_]] =
      node.getChildren.asScala.map(dn => fromModelDataNode(dn))(breakOut)
    SDataNode(node.getData, children)
  }
}
