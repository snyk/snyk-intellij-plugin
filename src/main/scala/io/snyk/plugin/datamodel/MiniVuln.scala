package io.snyk.plugin.datamodel

import cats.syntax.functor._
import io.circe.{Decoder, Encoder}
import io.circe.derivation.{deriveDecoder, deriveEncoder}
import io.circe.syntax._

import scala.collection.breakOut

case class ResultantDelta(
 pre  : Seq[MavenCoords],
 post : Seq[MavenCoords]
)

object ResultantDelta {
  implicit val encoder: Encoder[ResultantDelta] = deriveEncoder
  implicit val decoder: Decoder[ResultantDelta] = deriveDecoder
}

sealed trait Remediation {
  def optNewVersion: Option[String]
}

object Remediation {
  case object NoFix extends Remediation {
    override val optNewVersion: Option[String] = None
  }

  case class Upgrade(
    newVersion      : String,
    resultantDeltas : Set[ResultantDelta]
  ) extends Remediation {
    override val optNewVersion: Option[String] = Some(newVersion)
  }

  implicit val upgradeEncoder: Encoder[Upgrade] = deriveEncoder
  implicit val upgradeDecoder: Decoder[Upgrade] = deriveDecoder

  implicit val encoder: Encoder[Remediation] = Encoder.instance {
    case NoFix => io.circe.Json.Null
    case x @ Upgrade(_,_) => x.asJson
  }

  implicit val decodeEvent: Decoder[Remediation] = Decoder[Upgrade].widen or Decoder.const(NoFix)

}

case class VulnDerivation(
  module       : MavenCoords,
  remediations : Remediation
)

object VulnDerivation {
  implicit val encoder: Encoder[VulnDerivation] = deriveEncoder
  implicit val decoder: Decoder[VulnDerivation] = deriveDecoder

  def merge(xs: Seq[VulnDerivation]): Seq[VulnDerivation] = {
    for {
      moduleGrouped <- xs.groupBy(_.module).values.toSeq
      versionGrouped <- moduleGrouped.groupBy(_.remediation.optNewVersion).values.toSeq
    } yield {
      val module = versionGrouped.head.module
      versionGrouped.head.remediation match {
        case Remediation.NoFix =>
          VulnDerivation(module, Remediation.NoFix)
        case Remediation.Upgrade(newVersion, _) =>
          val mergedDeltas: Set[ResultantDelta] =
            versionGrouped.flatMap(_.remediation.asInstanceOf[Remediation.Upgrade].resultantDeltas)(breakOut)
          VulnDerivation(module, Remediation.Upgrade(newVersion, mergedDeltas))
      }
    }
  }
}

case class MiniVuln private (
  spec        : VulnSpec,
  from        : Option[MiniTree[MavenCoords]],
  upgradePath : Option[MiniTree[MavenCoords]]
)

object MiniVuln {
  def apply(
    spec        : VulnSpec,
    from        : Seq[MavenCoords],
    upgradePath : Seq[MavenCoords]
  ): MiniVuln = {
    MiniVuln(spec, MiniTree fromLinear from, MiniTree fromLinear upgradePath)
  }

  def from(vuln: Vulnerability): MiniVuln = MiniVuln(
    spec        = VulnSpec.from(vuln),
    upgradePath = vuln.upgradePath.collect{case Right(str) => MavenCoords.from(str)},
    from        = vuln.from.map(MavenCoords.from)
  )

  implicit val encoder: Encoder[MiniVuln] = deriveEncoder
  implicit val decoder: Decoder[MiniVuln] = deriveDecoder
}


