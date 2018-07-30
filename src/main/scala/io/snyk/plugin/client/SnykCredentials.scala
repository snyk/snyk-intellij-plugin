package io.snyk.plugin.client

import java.nio.file.{Path, Paths}
import java.util.UUID

import io.circe._
import io.circe.parser._
import io.circe.derivation._
import io.circe.syntax._
import java.io.PrintWriter

import scala.io.Source
import scala.util.Try

object SnykCredentials {
  implicit val decoder: Decoder[SnykCredentials] = deriveDecoder[SnykCredentials]
  implicit val encoder: Encoder[SnykCredentials] = deriveEncoder[SnykCredentials]

  def default: Try[SnykCredentials] = forPath(defaultConfigFile)

  def forPath(configFilePath: Path): Try[SnykCredentials] =
    Try { Source.fromFile(configFilePath.toFile).mkString } flatMap {
      str => decode[SnykCredentials](str).toTry
    }

  def defaultConfigFile: Path = Paths.get(System.getProperty("user.home"), ".config/configstore/snyk.json")
  def defaultEndpoint: String = "http://snyk.io/api"
}

import SnykCredentials._

case class SnykCredentials(
  api: UUID,
  endpoint: Option[String]
) {
  def endpointOrDefault: String = endpoint getOrElse defaultEndpoint
  def writeToFile(path: Path = defaultConfigFile): Unit = {
    val pw = new PrintWriter(path.toFile)
    pw.write(this.asJson.spaces4)
    pw.close()

  }
}



