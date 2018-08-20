package io.snyk.plugin.embeddedserver

import java.net.URLEncoder

import fi.iki.elonen.NanoHTTPD.IHTTPSession


/**
  * Represents known parameters against an internal http request.
  * Most notably, parses the `requires` parameter into a strongly-typed Set of
  * `Requirement` instances.
  *
  * This is an *essential* feature and drives much of the core logic of the plugin
  */
class ParamSet private (map: Map[String, Seq[String]]) {

  def contextMap: Map[String, Any] = map collect {
    case (k, v) if v.size == 1 => k -> v.head
    case (k, v) if v.nonEmpty => k -> v
  }

  def containsKey(key: String): Boolean = map.contains(key)
  def first(key: String): Option[String] = map.get(key).flatMap(_.headOption)
  def all(key: String): Seq[String] = map.get(key).toSeq.flatten.flatMap(_.split(','))
  def isTrue(key: String): Boolean = all(key).exists(_.toLowerCase == "true")

  def plus(x: (String, String)): ParamSet = {
    val newSeq = map.getOrElse(x._1, Seq.empty) :+ x._2
    new ParamSet(map + (x._1 -> newSeq))
  }

  def pathWildcard: String = first("pathWildcard") getOrElse ""

  def +(x: (String, String)): ParamSet = plus(x)

  override def toString: String = map.mkString

  def queryString: String =
    map.flatMap{case (k, vs) => vs.map(v => s"$k=${URLEncoder.encode(v, "UTF-8")}")}.mkString("?","&","")
}

object ParamSet {
  import scala.collection.JavaConverters._

  def from(session: IHTTPSession): ParamSet =
    new ParamSet(session.getParameters.asScala.toMap.mapValues(_.asScala.toSeq))

  val Empty = new ParamSet(Map.empty)
}
