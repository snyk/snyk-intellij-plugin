package io.snyk.plugin.model

import scala.util.Random

case class DisplayNode(
  name        : String,
  nested      : Seq[DisplayNode],
  hasHighVuln : Boolean = false,
  hasMedVuln  : Boolean = false,
  hasLowVuln  : Boolean = false,
  vulns       : Set[MiniVuln] = Set.empty
) {
  def performVulnAssociation(allVulns: Seq[MiniVuln]): DisplayNode = {
    val relevant = allVulns.filter(_.moduleName == this.name)
    this.copy(
      nested = nested.map(_.performVulnAssociation(allVulns)),
      hasHighVuln = relevant.exists(_.severity == "high"),
      hasMedVuln  = relevant.exists(_.severity == "medium"),
      hasLowVuln  = relevant.exists(_.severity == "low"),
      vulns = relevant.toSet
    )
  }

  def randomiseStatus: DisplayNode = {
    this.copy(
      nested = nested.map(_.randomiseStatus),
      hasHighVuln = Random.nextBoolean(),
      hasMedVuln  = Random.nextBoolean(),
      hasLowVuln  = Random.nextBoolean()
    )
  }
}

case class MiniVuln(
  title        : String,
  id           : String,
  severity     : String,
  moduleName   : String,
  versions     : Seq[String],
  isUpgradable : Boolean,
  isPatchable  : Boolean,
  isIgnored    : Boolean,
)
