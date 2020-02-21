package io.snyk.plugin.datamodel

import io.circe.{Decoder, ObjectEncoder}
import io.circe.derivation.{deriveDecoder, deriveEncoder}

case class VulnSpec(
                     title            : String,
                     id               : String,
                     severity         : String,
                     module           : VulnerabilityCoordinate,
                     affectedVersions : Seq[String],
                     isUpgradable     : Boolean,
                     isPatchable      : Boolean,
                     isIgnored        : Boolean,
                     filterInfo       : Option[VulnFilteredInfo]
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
  def toMultiString: Seq[String] = Seq(
    s"title              = $title           ",
    s"  id               = $id              ",
    s"  severity         = $severity        ",
    s"  module           = $module          ",
    s"  affectedVersions = $affectedVersions",
    s"  isUpgradable     = $isUpgradable    ",
    s"  isPatchable      = $isPatchable     ",
    s"  isIgnored        = $isIgnored       ",
  )
}

object VulnSpec {
  implicit val ordering: Ordering[VulnSpec] = Ordering.by(x => (x.severityRank, x.module.name, x.id))

  def from(vuln: SecurityVuln): VulnSpec = VulnSpec(
    title            = vuln.title,
    id               = vuln.id,
    module           = VulnerabilityCoordinate.from(vuln.moduleName, vuln.version),
    severity         = vuln.severity,
    affectedVersions = vuln.semver.vulnerable,
    isUpgradable     = vuln.isUpgradable,
    isPatchable      = vuln.isPatchable,
    isIgnored        = vuln.filtered.isDefined,
    filterInfo       = vuln.filtered
  )

  implicit val encoder: ObjectEncoder[VulnSpec] = deriveEncoder
  implicit val decoder: Decoder[VulnSpec] = deriveDecoder
}
