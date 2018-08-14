package io.snyk.plugin.datamodel

import io.circe.{Decoder, Encoder}
import io.circe.derivation.{deriveDecoder, deriveEncoder}

case class MiniTree[+T] (content: T, nested: Seq[MiniTree[T]]) {
  def linearise: Seq[Seq[T]] = MiniTree.toLinear(this)
  def depth: Int = 1 + ( if(nested.isEmpty) 0 else nested.map(_.depth).max )
  def treeString: Seq[String] =
    s"$content" +: nested.flatMap(_.treeString).map("| " + _)

}

object MiniTree {

  implicit def encoder[T: Encoder]: Encoder[MiniTree[T]] = deriveEncoder
  implicit def decoder[T: Decoder]: Decoder[MiniTree[T]] = deriveDecoder

  def apply[T](content: T, nested: MiniTree[T]*)(implicit dummy: Int = 0): MiniTree[T] =
    MiniTree(content, nested.toSeq)

  def fromLinear[T](xs: Traversable[T]): Option[MiniTree[T]] =
    if(xs.isEmpty) None else Some(MiniTree(xs.head, fromLinear(xs.tail).toSeq))

  def toLinear[T](rootTree: MiniTree[T]): Seq[Seq[T]] = {
    def dft(
      mt: MiniTree[T],
      done: Seq[Seq[T]],
      acc: Seq[T],
      resume: Option[(Seq[T], MiniTree[T])]
    ): Seq[Seq[T]] = {
      val newAcc = acc :+ mt.content

      if(mt.nested.isEmpty) {
        resume match {
          case None => done :+ newAcc
          case Some((acc, r)) => dft(r, done :+ newAcc, acc, None)
        }
      } else {
        val newResume: Option[(Seq[T], MiniTree[T])] = resume orElse {
          if (mt.nested.tail.isEmpty) None
          else Some(acc -> mt.copy(nested = mt.nested.tail))
        }
        dft(mt.nested.head, done, newAcc, newResume)
      }
    }
    dft(rootTree, Nil, Nil, None)
  }

  def merge[T](seq: Seq[MiniTree[T]]): Seq[MiniTree[T]] = {
    val nonEmpty = seq collect { case node: MiniTree[T] => node }
    nonEmpty.groupBy(_.content).values.map { xs =>
      MiniTree(xs.head.content, merge(xs.flatMap(_.nested)))
    }.toSeq
  }
}

