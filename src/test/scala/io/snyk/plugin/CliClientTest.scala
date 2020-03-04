package io.snyk.plugin

import io.snyk.plugin.client.{CliClient, SnykConfig}
import org.junit.Test
import org.junit.Assert._

class CliClientTest {

  @Test
  def testUserInfoEndpoint(): Unit = {
    val config = SnykConfig.default
    val apiClient = CliClient.standard(config)

    assertNotNull(apiClient)
    assertTrue(apiClient.userInfo().isSuccess)
  }
}
