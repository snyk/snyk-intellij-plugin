package io.snyk.plugin

import io.circe.parser.decode
import io.snyk.plugin.client.SnykUserResponse
import org.junit.Test
import org.junit.Assert._

import scala.io.Source

class SnykUserResponseTest {

  @Test
  def testParseSnykUserResponse(): Unit = {
    val inputStream =  getClass.getClassLoader.getResourceAsStream("sample-userinfo.json")
    val inputSource = Source.fromInputStream(inputStream)
    val inputString = inputSource.mkString
    val output = decode[SnykUserResponse](inputString)

    assertNotNull(output)
    assertEquals("sample.user@snyk.io", output.getOrElse("").asInstanceOf[SnykUserResponse].user.email)
  }
}
