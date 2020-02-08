package io.snyk.plugin

import io.snyk.plugin.client.{SnykCLIClient, SnykConfig}
import org.junit.Test
import org.junit.Assert._

class SnykCLIClientTest {

  @Test
  def testUserInfoEndpoint(): Unit = {
    val config = SnykConfig.default
    val apiClient = SnykCLIClient.standard(config)

    assertNotNull(apiClient)
    assertTrue(apiClient.userInfo().isSuccess)
  }
}
