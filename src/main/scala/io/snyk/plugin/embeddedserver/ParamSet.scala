package io.snyk.plugin.embeddedserver

import fi.iki.elonen.NanoHTTPD.IHTTPSession
import enumeratum._

sealed trait Requirement extends EnumEntry

object Requirement extends Enum[Requirement] {
  val values = findValues

  case object Auth                   extends Requirement
  case object NewScan                extends Requirement
  case object DepTree                extends Requirement
  case object DepTreeAnnotated       extends Requirement
  case object DepTreeRandomAnnotated extends Requirement
  case object MiniVulns              extends Requirement
  case object FullVulns              extends Requirement
}

/**
  * Represents known parameters against an internal http request.
  * Most notably, parses the `requires` parameter into a strongly-typed Set of
  * `Requirement` instances.
  */
class ParamSet private (map: Map[String, Seq[String]], val requires: Set[Requirement]) {
  def containsKey(key: String): Boolean = map.contains(key)
  def first(key: String): Option[String] = map.get(key).flatMap(_.headOption)
  def all(key: String): Seq[String] = map.get(key).toSeq.flatten
  def isTrue(key: String): Boolean = all(key).exists(_.toLowerCase == "true")

  def without(req: Requirement): ParamSet = new ParamSet(map, requires - req)

  def needsScanResult: Boolean =
    requires(Requirement.NewScan) ||
    requires(Requirement.DepTreeAnnotated) ||
    requires(Requirement.MiniVulns) ||
    requires(Requirement.FullVulns)

  override def toString: String = map.mkString + ",requires=" + requires.mkString(",")

  private[this] def reqsQueryPart: String =
    if(requires.isEmpty) ""
    else "&requires=" + requires.map(_.entryName).mkString(",")

  def queryString: String =
    map.flatMap{case (k, vs) => vs.map(v => s"$k=$v")}.mkString("?","&","") + reqsQueryPart
}

object ParamSet {
  def from(session: IHTTPSession): ParamSet = {
    import scala.collection.JavaConverters._
    val map = session.getParameters.asScala.toMap.mapValues(_.asScala.toSeq)

    def requires: Set[Requirement] = {
      map.getOrElse("requires", Seq.empty)
        .flatMap(_.split(','))
        .map(_.trim)
        .flatMap(Requirement.withNameInsensitiveOption)
        .toSet
    }

    new ParamSet(map - "requires", requires)
  }

  val Empty = new ParamSet(Map.empty, Set.empty)
}
