package io.snyk.plugin.embeddedserver

import fi.iki.elonen.NanoHTTPD.IHTTPSession

class ParamSet private (map: Map[String, Seq[String]]) {
  def containsKey(key: String): Boolean = map.contains(key)
  def first(key: String): Option[String] = map.get(key).flatMap(_.headOption)
  def all(key: String): Seq[String] = map.get(key).toSeq.flatten
  def isTrue(key: String): Boolean = all(key).exists(_.toLowerCase == "true")
}

object ParamSet {
  def from(session: IHTTPSession): ParamSet = {
    import scala.collection.JavaConverters._
    val map = session.getParameters.asScala.toMap.mapValues(_.asScala.toSeq)
    new ParamSet(map)
  }
}
