package io.snyk.plugin.client

import java.net.URI
import java.time.OffsetDateTime
import java.util.UUID

import io.circe.{Decoder, Encoder, Json, ObjectEncoder}
import io.circe.derivation.{deriveDecoder, deriveEncoder}
import io.circe.java8.time._
import cats.implicits._

case class SnykUserResponse(ok: Boolean, user: SnykUserInfo)

object SnykUserResponse {
  implicit val decoder: Decoder[SnykUserResponse] = deriveDecoder[SnykUserResponse]
  implicit val encoder: ObjectEncoder[SnykUserResponse] = deriveEncoder[SnykUserResponse]
}

case class SnykUserInfo(
  username: String,
  name: String,
  email: String,
  created: OffsetDateTime,
  avatar: URI,
  id: UUID
)

object SnykUserInfo {

  implicit val encodeUri: Encoder[URI] = (uri: URI) => Json.fromString(uri.toString)
  implicit val encodeUuid: Encoder[UUID] = (uuid: UUID) => Json.fromString(uuid.toString)


  implicit val decodeUri: Decoder[URI] = Decoder.decodeString.emap { str =>
    Either.catchNonFatal(URI.create(str)).leftMap(_ => "URI")
  }
  implicit val decodeUuid: Decoder[UUID] = Decoder.decodeString.emap { str =>
    Either.catchNonFatal(UUID.fromString(str)).leftMap(_ => "URI")
  }

  implicit val decoder: Decoder[SnykUserInfo] = deriveDecoder[SnykUserInfo]
  implicit val encoder: ObjectEncoder[SnykUserInfo] = deriveEncoder[SnykUserInfo]
}
