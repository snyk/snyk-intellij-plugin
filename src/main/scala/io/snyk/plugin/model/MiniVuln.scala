package io.snyk.plugin.model

case class MavenCoords(group: String, name: String, version: String) {
  def unversionedName: String =  s"$group:$name"
  def shortName: String =  s"$name@$version"
  override def toString: String = s"$group:$name@$version"
}

object MavenCoords {
  def from(fullVersionedName: String): MavenCoords = {
    val arr1 = fullVersionedName.split('@')
    val arr2 = arr1.head.split(':')
    MavenCoords(arr2(0), arr2(1), arr1(1))
  }

  def from(fullName: String, version: String): MavenCoords = {
    val arr = fullName.split(':')
    MavenCoords(arr(0), arr(1), version)
  }
}

case class VulnSpec(
  title            : String,
  id               : String,
  severity         : String,
  module           : MavenCoords,
  affectedVersions : Seq[String],
  isUpgradable     : Boolean,
  isPatchable      : Boolean,
  isIgnored        : Boolean,
) {
  /** for sorting **/
  def severityRank: Int = severity match {
    case "high" => 1
    case "medium" => 2
    case "low" => 3
    case _ => 99
  }
  //this lot for the handlebars templates
  def `type`: String = "vuln"
  def url: String = s"https://snyk.io/vuln/$id"
  def icon: String = "maven"
  def affectedVersionsStr: String = affectedVersions.mkString(" & ")
  def isPatched: Boolean = false
  def isConfidential: Boolean = false
  def issueType: String = "vuln"
}

object VulnSpec {
  implicit val ordering: Ordering[VulnSpec] =
    Ordering.by(x => (x.severityRank, x.module.name, x.id))
}

sealed trait Remediation

object Remediation {
  case object NoFix extends Remediation

  case class Upgrade(
    newVersion : String,
    depsPre    : Seq[MavenCoords],
    depsPost   : Seq[MavenCoords]
  ) extends Remediation
}

case class VulnDerivation(
  module       : MavenCoords,
  remediations : Set[Remediation]
)

case class MiniVuln private (
  spec        : VulnSpec,
  from        : MiniTree[MavenCoords],
  upgradePath : MiniTree[MavenCoords]
) {
  //def derivations : MiniTree[VulnDerivation]
}

object MiniVuln {
//  def merge(occurrences: Seq[MiniVuln]): Seq[MiniVuln] = {
//    val seq = occurrences.groupBy(_.spec).values.toSeq
//    seq flatMap { xs =>
//      val froms = MiniTree.merge(xs.map(_.derivations))
//      froms
//    }
//  }
  def apply(
    spec        : VulnSpec,
    from        : Seq[MavenCoords],
    upgradePath : Seq[MavenCoords]
  ): MiniVuln = {
//    val derivations = if(from.isEmpty) {
//      MiniTree fromLinear from.map(VulnDerivation(_, Set(Remediation.NoFix)))
//    } else {
//
//    }
      MiniVuln(spec, MiniTree fromLinear from, MiniTree fromLinear upgradePath)
  }
}

