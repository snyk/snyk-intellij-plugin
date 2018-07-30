package io.snyk.plugin.client

import java.nio.file.{Path, Paths}

import io.circe._
import io.circe.parser._

import scala.io.Source
import scala.util.{Failure, Success, Try}

object SnykCredentials {
  def default: SnykCredentials = forPath(Paths.get(System.getProperty("user.home"), ".config/configstore/snyk.json"))
  def forPath(configFilePath: Path): SnykCredentials = new SnykCredentials(configFilePath)

  implicit class RichOption[T](val opt: Option[T]) extends AnyVal {
    def toTryOr(errMsg: String): Try[T] = opt match {
      case Some(o) => Success.apply(o)
      case None => Failure(new RuntimeException(errMsg))
    }
  }

  implicit class RichJson(val j: Json) extends AnyVal {
    def tryAsObject: Try[JsonObject] = j.asObject.toTryOr(s"config json is not an object: ${j.toString}")
    def tryToString: Try[String] = j.asString.toTryOr(s"json value is not a string: ${j.toString}")
  }

  implicit class RichJsonObject(val j: JsonObject) extends AnyVal {
    def tryGet(key: String): Try[Json] = j(key).toTryOr(s"key [$key] not present in JSON object: ${j.toString}")
  }
}

class SnykCredentials private(configFilePath: Path) {
  import SnykCredentials._

  private[this] lazy val configFile = configFilePath.toFile
  private[this] lazy val configJsonStr: Try[String] = Try { Source.fromFile(configFile).mkString }
  private[this] lazy val configJson: Try[JsonObject] = for {
    str <- configJsonStr
    json <- parse(str).toTry
    jsonObj <- json.tryAsObject
  } yield jsonObj

  def configFileExists: Boolean = configFile.exists()

  def get(key: String): Try[Json] = configJson.flatMap{_.tryGet(key)}
  def getString(key: String): Try[String] = get(key).flatMap(_.tryToString)

  def endpoint: String = getString("endpoint").getOrElse("http://snyk.io/api")
  def apiToken: Try[String] = getString("api")

  //    val apiEndpoint = "http://snyk.io/api"
  //    val apiToken = "2979c2e5-019d-48fd-9f0b-895ec6e6a4d5"

//  val apiEndpoint = "http://dev.snyk.io/api"
//  val apiToken = "71e3aa89-03bc-4005-a050-727e68d762eb"

  //    val apiEndpoint = "http://localhost:8000/api"
  //    val apiToken = "e97cd45b-e011-4ac8-a898-dad07e47d736"
}


