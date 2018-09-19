import Predef.{any2stringadd => _, _}
import io.circe.parser._

import scala.io.Source
import io.snyk.plugin.client.SnykUserResponse

object TestParseUserInfo extends App {
  val inputStream =  getClass.getClassLoader.getResourceAsStream("sample-userinfo.json")
  val inputSource = Source.fromInputStream(inputStream)
  val input = inputSource.mkString
  val output = decode[SnykUserResponse](input)

  println(output)
}
