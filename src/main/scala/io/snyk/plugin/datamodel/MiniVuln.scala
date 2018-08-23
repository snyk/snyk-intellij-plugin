package io.snyk.plugin.datamodel

import io.circe.{Decoder, Encoder}
import io.circe.derivation.{deriveDecoder, deriveEncoder}

import scala.annotation.tailrec
import io.snyk.plugin.IntellijLogging


case class VulnDerivation(
  module       : MavenCoords,
  remediations : Map[String, Seq[MiniTree[MavenCoords]]]
)

object VulnDerivation {
  implicit val encoder: Encoder[VulnDerivation] = deriveEncoder
  implicit val decoder: Decoder[VulnDerivation] = deriveDecoder

  def merge(xs: Seq[VulnDerivation]): Seq[VulnDerivation] = {
    for {
      moduleGrouped <- xs.groupBy(_.module).values.toSeq
    } yield {
      val module = moduleGrouped.head.module
      val remediationMaps = moduleGrouped.map(_.remediations)

      val aggregatedRemediationMap = remediationMaps.flatMap(_.toSeq)
      val groupedByVersion = aggregatedRemediationMap.groupBy(_._1).mapValues(_.flatMap(_._2))

      val merged = groupedByVersion.mapValues(MiniTree.merge)
      VulnDerivation(module, merged)
    }
  }
}

case class MiniVuln(
  spec        : VulnSpec,
  derivations : Seq[MiniTree[VulnDerivation]]
)

object MiniVuln extends IntellijLogging {

  def derivHasRemediations(tree: MiniTree[VulnDerivation]): Boolean =
    tree.content.remediations.nonEmpty ||
      tree.nested.exists(derivHasNestedRemediations)

  def derivHasNestedRemediations(tree: MiniTree[VulnDerivation]): Boolean =
      tree.nested.exists(derivHasRemediations)


  def merge(occurrences: Seq[MiniVuln]): Seq[MiniVuln] = {

    /**
      * Merges peer nodes that share a module ID by summing their remediation maps
      */
    def collapse(miniTrees: Seq[MiniTree[VulnDerivation]]): Seq[MiniTree[VulnDerivation]] = {
      if(miniTrees.size > 1) {
        val groupedByModule = miniTrees.groupBy(_.content.module)
        groupedByModule.map{ case (module, trees) =>
          val remediationMaps = trees.flatMap(_.content.remediations).groupBy(_._1).mapValues(_.flatMap(_._2))
          val mergedRemediationMaps = remediationMaps.mapValues(MiniTree.merge)
          val kids = trees.flatMap(_.nested)
          val mergedKids = MiniTree.merge(kids)
          MiniTree(VulnDerivation(module, mergedRemediationMaps), mergedKids)
        }.toSeq
      } else {
        miniTrees map { t => t.copy(nested = collapse(t.nested)) }
      }
    }

    val specDerivsMap = occurrences.groupBy(_.spec).mapValues(_.flatMap(_.derivations))

    specDerivsMap.map{ case (spec, trees) =>
      val mergedTrees = MiniTree merge trees
      val collapsedTrees = collapse(mergedTrees)
      MiniVuln(spec, collapsedTrees)
    }.toSeq
  }

  @tailrec private[this] def mkDerivationSeq(
    from        : Seq[String],
    upgradePath : Seq[Either[Boolean, String]],
    acc         : Seq[VulnDerivation] = Nil
  ): Seq[VulnDerivation] = {
    (from, upgradePath) match {
      case (_, Left(true) +: _) => ???

      case (fHead +: fTail, Left(false) +: upTail) =>
        mkDerivationSeq(
          fTail,
          upTail,
          acc :+ VulnDerivation(MavenCoords.from(fHead), Map.empty)
        )

      case (fHead +: fTail, up) if up.isEmpty =>
        mkDerivationSeq(
          fTail,
          Nil,
          acc :+ VulnDerivation(MavenCoords.from(fHead), Map.empty)
        )

      case (fHead +: fTail, Right(upHead) +: upTail) =>
        val newCoords = MavenCoords.from(upHead)
        val newVersion = newCoords.version
        val pivotSeq = upTail collect { case Right(ver) => MavenCoords.from(ver) }
        val pivot = MiniTree.fromLinear(pivotSeq) map { t => Map(newVersion -> Seq(t)) } getOrElse Map.empty
        mkDerivationSeq(
          fTail,
          Nil,
          acc :+ VulnDerivation(MavenCoords.from(fHead), pivot)
        )

      case (f, _) if f.isEmpty => acc
      case _ =>
        log.debug("FROM\n" + from.mkString("\n"))
        log.debug("UPGRADE\n" + upgradePath.mkString("\n"))
        log.debug("ACC\n" + acc.mkString("\n"))
        ???
    }
  }

  def from(vuln: Vulnerability): MiniVuln = {
    val dseq = mkDerivationSeq(vuln.from, vuln.upgradePath)
    val dtree = MiniTree.fromLinear(dseq).toSeq
    MiniVuln(spec = VulnSpec.from(vuln), derivations = dtree)
  }

  def mergedFrom(vulns: Seq[Vulnerability]): Seq[MiniVuln] =
    merge(vulns.map(MiniVuln.from))

  implicit val encoder: Encoder[MiniVuln] = deriveEncoder
  implicit val decoder: Decoder[MiniVuln] = deriveDecoder
}

