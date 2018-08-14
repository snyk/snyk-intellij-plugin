package io.snyk.plugin.client

import io.circe.derivation.{deriveDecoder, deriveEncoder}
import io.circe.syntax._
import io.circe._
import io.snyk.plugin.datamodel.SnykMavenArtifact

object SnykClientSerialisation {
  case class JsonForm(
    groupId: String,
    artifactId: String,
    packaging: String,
    version: String,
    name: String,
    dependencies: Map[String, JsonForm],
    scope: Option[String]
  )

  case class RootJsonForm(
    groupId: String,
    artifactId: String,
    packaging: String,
    version: String,
    name: String,
    dependencies: Map[String, JsonForm],
    packageFormatVersion: String = "mvn:0.0.1",
  )

  private def jsonForm(a: SnykMavenArtifact): JsonForm = JsonForm(
    a.groupId,
    a.artifactId,
    a.packaging,
    a.version,
    a.name,
    a.depsMap.mapValues(jsonForm),
    a.scope
  )

  private def rootJsonForm(a: SnykMavenArtifact): RootJsonForm = RootJsonForm(
    a.groupId,
    a.artifactId,
    a.packaging,
    a.version,
    a.name,
    a.depsMap.mapValues(jsonForm)
  )

  private implicit val decoderJsonForm: Decoder[JsonForm] = deriveDecoder
  private implicit val decoderRootJsonForm: Decoder[RootJsonForm] = deriveDecoder
  private implicit val encoderJsonForm: Encoder[JsonForm] = deriveEncoder
  private implicit val encoderRootJsonForm: RootEncoder[RootJsonForm] = deriveEncoder

  def encode(a: SnykMavenArtifact): Json = jsonForm(a).asJson
  def encodeRoot(a: SnykMavenArtifact): Json = rootJsonForm(a).asJson
}
