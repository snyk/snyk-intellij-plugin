package io.snyk.plugin.client

import io.circe.derivation.{deriveDecoder, deriveEncoder}
import io.circe.syntax._
import io.circe._
import io.snyk.plugin.datamodel.ProjectDependency

object SnykClientSerialisation {
  case class QueryString(org: String)

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
    qs: Option[QueryString] = None,
    packageFormatVersion: String = "mvn:0.0.1",
  )

  private def jsonForm(a: ProjectDependency): JsonForm = JsonForm(
    a.groupId,
    a.artifactId,
    a.packaging,
    a.version,
    a.name,
    a.depsMap.mapValues(jsonForm),
    a.scope
  )

  private def rootJsonForm(a: ProjectDependency): RootJsonForm = RootJsonForm(
    a.groupId,
    a.artifactId,
    a.packaging,
    a.version,
    a.name,
    a.depsMap.mapValues(jsonForm)
  )

  private implicit val decoderQueryString: Decoder[QueryString] = deriveDecoder
  private implicit val encoderQueryString: Encoder[QueryString] = deriveEncoder
  private implicit val decoderJsonForm: Decoder[JsonForm] = deriveDecoder
  private implicit val decoderRootJsonForm: Decoder[RootJsonForm] = deriveDecoder
  private implicit val encoderJsonForm: Encoder[JsonForm] = deriveEncoder
  private implicit val encoderRootJsonForm: RootEncoder[RootJsonForm] = deriveEncoder

  def encode(a: ProjectDependency): Json = jsonForm(a).asJson
  def encodeRoot(a: ProjectDependency, org: Option[String] = None): Json = org match {
    case Some(str) => rootJsonForm(a).copy(qs = Some(QueryString(org = str))).asJson
    case None => rootJsonForm(a).asJson
  }

}
