package io.snyk.plugin.datamodel

import io.circe.{Decoder, Encoder}
import io.circe.derivation.{deriveDecoder, deriveEncoder}

import scala.annotation.tailrec

case class MiniVuln2(
  spec        : VulnSpec,
  derivations : Seq[MiniTree[VulnDerivation]]
)

object MiniVuln2 {
  def merge(occurrences: Seq[MiniVuln2]): Seq[MiniVuln2] = {
    def collapse(miniTree: MiniTree[VulnDerivation]): MiniTree[VulnDerivation] = {
      miniTree.nested match {
        case seq if seq.size > 1 =>
          println("WALK: ")
          val mergedDerivs = VulnDerivation.merge(miniTree.nested.map(_.content)).map(MiniTree(_, Nil))
          mergedDerivs.foreach(_.treeString.foreach(println))
          miniTree.copy(nested = mergedDerivs)
        case seq => miniTree.copy(nested = miniTree.nested.map(collapse))
      }
    }
    val mergedTrees =
      occurrences.groupBy(_.spec).values.toSeq map { xs =>
        //where xs is each seq of vulns sharing a spec
        val flatDerivations = xs.flatMap(_.derivations)
        val derivations = MiniTree.merge(flatDerivations)
        MiniVuln2(xs.head.spec, derivations.map(collapse))
      }

    mergedTrees
  }

  @tailrec private[this] def mkDerivationSeq(
    from        : Seq[String],
    upgradePath : Seq[Either[Boolean, String]],
    acc         : Seq[VulnDerivation] = Nil
  ): Seq[VulnDerivation] = {
    import Remediation._
    (from, upgradePath) match {
      case (_, Left(true) +: _) => ???

      case (fHead +: fTail, Left(false) +: upTail) =>
        mkDerivationSeq(
          fTail,
          upTail,
          acc :+ VulnDerivation(MavenCoords.from(fHead), NoFix)
        )

      case (fHead +: fTail, up) if up.isEmpty =>
        mkDerivationSeq(
          fTail,
          Nil,
          acc :+ VulnDerivation(MavenCoords.from(fHead), NoFix)
        )

      case (fHead +: fTail, Right(upHead) +: upTail) =>
        val newVersion = MavenCoords.from(upHead).version
        val resultantDelta = ResultantDelta(
          fTail.map(MavenCoords.from),
          upTail.collect{case Right(str) => MavenCoords.from(str)}
        )
        acc :+ VulnDerivation(MavenCoords.from(fHead), Upgrade(newVersion, Set(resultantDelta)))

      case (f, _) if f.isEmpty => acc
      case _ =>
        println("FROM\n" + from.mkString("\n"))
        println("UPGRADE\n" + upgradePath.mkString("\n"))
        println("ACC\n" + acc.mkString("\n"))
        ???
    }
  }

  def from(vuln: Vulnerability): MiniVuln2 = MiniVuln2(
    spec        = VulnSpec.from(vuln),
    derivations = MiniTree.fromLinear(mkDerivationSeq(vuln.from, vuln.upgradePath)).toSeq
  )

  def mergedFrom(vulns: Seq[Vulnerability]): Seq[MiniVuln2] =
    merge(vulns.map(MiniVuln2.from))

  implicit val encoder: Encoder[MiniVuln2] = deriveEncoder
  implicit val decoder: Decoder[MiniVuln2] = deriveDecoder
}

