package io.snyk.plugin

import io.snyk.plugin.client.{ApiClient, SnykConfig}
import org.junit.Test
import org.junit.Assert._

class ApiClientTest {

  @Test
  def testUserInfoEndpoint(): Unit = {
    val config = SnykConfig.default
    val apiClient = ApiClient.standard(config)

    assertNotNull(apiClient)
    assertTrue(apiClient.userInfo().isSuccess)
  }
}
