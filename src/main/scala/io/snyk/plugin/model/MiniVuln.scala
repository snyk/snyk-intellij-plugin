package io.snyk.plugin.model

case class MiniVuln(
  title            : String,
  id               : String,
  severity         : String,
  moduleName       : String,
  version          : String,
  affectedVersions : Seq[String],
  upgradePath      : Seq[String],
  from             : Seq[String],
  isUpgradable     : Boolean,
  isPatchable      : Boolean,
  isIgnored        : Boolean,
) {
  def severityRank: Int = severity match {
    case "high" => 1
    case "medium" => 2
    case "low" => 3
    case _ => 99
  }
  //this lot for the handlebars templates
  def versionedName: String = s"$moduleName@$version"
  def `type`: String = "vuln"
  def url: String = s"https://snyk.io/vuln/$id"
  def `package`: String = moduleName
  def icon: String = "maven"
  def affectedVersionsStr: String = affectedVersions.mkString(" & ")
  def isPatched: Boolean = false
  def isConfidential: Boolean = false
  def issueType: String = "vuln"
  def fromStr: String = from.mkString(" » ")
  def intermediateFrom: Seq[String] = from.tail.init
}

