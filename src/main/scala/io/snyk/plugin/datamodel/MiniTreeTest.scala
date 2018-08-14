package io.snyk.plugin.datamodel

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
