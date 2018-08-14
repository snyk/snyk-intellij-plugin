package io.snyk.plugin.datamodel

import io.circe.{Decoder, Encoder}

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

  implicit val encoder: Encoder[MavenCoords] = Encoder.encodeString.contramap[MavenCoords](_.toString)
  implicit val decoder: Decoder[MavenCoords] = Decoder.decodeString.map(from)
}

