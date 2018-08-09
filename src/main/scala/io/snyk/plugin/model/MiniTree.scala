package io.snyk.plugin.model

sealed trait MiniTree[+T] {
  def linearise: Seq[Seq[T]]
  def depth: Int
  def treeString: Seq[String]
}
case class MiniTreeNode[+T] private (content: T, nested: Seq[MiniTreeNode[T]]) extends MiniTree[T] {
  override def linearise: Seq[Seq[T]] = MiniTree.toLinear(this)
  override def depth: Int = 1 + ( if(nested.isEmpty) 0 else nested.map(_.depth).max )
  override def treeString: Seq[String] =
    s"$content" +: nested.flatMap(_.treeString).map("| " + _)

}

object MiniTree {
  object Empty extends MiniTree[Nothing] {
    override val linearise = Nil
    override def depth = 0
    override def toString: String = "Empty MiniTree"
    def treeString: Seq[String] = Seq("Empty MiniTree")
  }

  def apply[T](content: T, nested: MiniTreeNode[T]*): MiniTreeNode[T] =
    MiniTreeNode(content, nested.toSeq)

  def fromLinear[T](xs: Traversable[T]): MiniTree[T] = {
    if(xs.isEmpty) Empty else {
      val nested = Seq(fromLinear(xs.tail)) collect { case node: MiniTreeNode[T] => node }
      MiniTreeNode(xs.head, nested)
    }
  }

  def toLinear[T](rootTree: MiniTree[T]): Seq[Seq[T]] = {
    def dft(
      mt: MiniTreeNode[T],
      done: Seq[Seq[T]],
      acc: Seq[T],
      resume: Option[(Seq[T], MiniTreeNode[T])]
    ): Seq[Seq[T]] = {
      val newAcc = acc :+ mt.content

      if(mt.nested.isEmpty) {
        resume match {
          case None => done :+ newAcc
          case Some((acc, r)) => dft(r, done :+ newAcc, acc, None)
        }
      } else {
        val newResume: Option[(Seq[T], MiniTreeNode[T])] = resume orElse {
          if (mt.nested.tail.isEmpty) None
          else Some(acc -> mt.copy(nested = mt.nested.tail))
        }
        dft(mt.nested.head, done, newAcc, newResume)
      }
    }
    rootTree match {
      case Empty => Nil
      case mtn: MiniTreeNode[T] => dft(mtn, Nil, Nil, None)
    }
  }

  def merge[T](seq: Seq[MiniTree[T]]): Seq[MiniTreeNode[T]] = {
    val nonEmpty = seq collect { case node: MiniTreeNode[T] => node }
    nonEmpty.groupBy(_.content).values.map { xs =>
      MiniTree(xs.head.content, merge(xs.flatMap(_.nested)): _*)
    }.toSeq
  }
}

object MiniTreeTest extends App {
  val mt = MiniTree("a",
    MiniTree("b",
      MiniTree("c",
        MiniTree("d")
      ),
      MiniTree("x",
        MiniTree("d")
      ),
      MiniTree("z",
        MiniTree("f")
      )
    )
  )

  val mt2 = MiniTree("a",
    MiniTree("b",
      MiniTree("c",
        MiniTree("d")
      ),
      MiniTree("nx",
        MiniTree("d")
      ),
      MiniTree("z",
        MiniTree("nf")
      )
    ),
    MiniTree("na")
  )
  println(mt.linearise)
  println("-----------------")
  println(MiniTree.merge(Seq(mt, mt2)).map(_.linearise).mkString("\n"))
}

/*
a -> b -> c -> d
a -> b -> x -> d

a
  b
    c
      d
    x
      d

a
a b
a b c      a b x
a b c d    a b x d
*/
