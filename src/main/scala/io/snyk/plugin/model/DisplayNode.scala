package io.snyk.plugin.model

import scala.util.Random

/**
  * A cut-down version of a node from the dep tree, enriched with vulnerability information.
  * Provides an abstraction over differences in Maven/Gradle/SBT and optimised for display
  * via handlebars templates.
  */
case class DisplayNode(
  name        : String,
  version     : String,
  nested      : Seq[DisplayNode] = Seq.empty,
  highVulns   : Int              = 0,
  medVulns    : Int              = 0,
  lowVulns    : Int              = 0,
  vulns       : Set[MiniVuln]    = Set.empty
) {
  def hasHighVulns: Boolean = highVulns > 0
  def hasMedVulns: Boolean = medVulns > 0
  def hasLowVulns: Boolean = lowVulns > 0

  def performVulnAssociation(allVulns: Seq[MiniVuln]): DisplayNode = {
    val relevant = allVulns.filter(_.moduleName == this.name)
    this.copy(
      nested = nested.map(_.performVulnAssociation(allVulns)),
      highVulns = highVulns + relevant.count(_.severity.toLowerCase=="high"),
      medVulns  = medVulns  + relevant.count(_.severity.toLowerCase.startsWith("med")),
      lowVulns  = lowVulns  + relevant.count(_.severity.toLowerCase=="low"),
      vulns = relevant.toSet
    )
  }

  def randomiseStatus: DisplayNode = {
    this.copy(
      nested = nested.map(_.randomiseStatus),
      highVulns = Random.nextInt(9),
      medVulns  = Random.nextInt(9),
      lowVulns  = Random.nextInt(9)
    )
  }
}

object DisplayNode {
  val Empty = DisplayNode(name = "<Empty Node>", version = "<Empty Node>")
}

