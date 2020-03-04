package io.snyk.plugin

import io.snyk.plugin.client.CliClient
import io.snyk.plugin.metrics.SegmentApi
import org.junit.Test
import org.junit.Assert._

class SegmentApiTest {

  @Test
  def testSendSegmentTestEvent(): Unit = {
    val clientMock = CliClient.mock(null)
    val segmentApi = SegmentApi(clientMock)

    assertNotNull(segmentApi)

    val result = segmentApi.track(
      "test event",
      Map(
        "a" -> 1,
        "b" -> 4.2,
        "c" -> 0.01d,
        "d" -> "foo"
      )
    )

    assertTrue(result.isSuccess)
  }
}
