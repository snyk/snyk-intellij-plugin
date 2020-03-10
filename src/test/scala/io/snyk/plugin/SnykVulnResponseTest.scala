package io.snyk.plugin

import io.circe.parser.decode
import io.snyk.plugin.datamodel.SnykVulnResponse
import io.snyk.plugin.datamodel.SnykVulnResponse.JsonCodecs._
import org.junit.Test
import org.junit.Assert._

import scala.io.{Codec, Source}

class SnykVulnResponseTest {

  @Test
  def testParseResponseWithEmptyVulnerabilities(): Unit = {
    val inputJsonStr = Source.fromResource("sample-response-empty-vulnerabilities.json", getClass.getClassLoader)(Codec.UTF8).mkString
    val tryOutput = decode[SnykVulnResponse](inputJsonStr)

    assertTrue(tryOutput.isRight)

    val snykVulnResponse = tryOutput.right.get

    assertNotNull(snykVulnResponse)
    assertNotNull(snykVulnResponse.vulnerabilities)

    assertEquals(0, snykVulnResponse.vulnerabilities.get.size)
  }

  @Test
  def testParseResponseWithVulnerabilities(): Unit = {
    val inputJsonStr = Source.fromResource("sample-response-3.json", getClass.getClassLoader)(Codec.UTF8).mkString
    val tryOutput = decode[SnykVulnResponse](inputJsonStr)

    assertTrue(tryOutput.isRight)

    val snykVulnResponse = tryOutput.right.get

    assertNotNull(snykVulnResponse)
    assertNotNull(snykVulnResponse.vulnerabilities)

    assertEquals(1, snykVulnResponse.vulnerabilities.get.size)
  }
}
