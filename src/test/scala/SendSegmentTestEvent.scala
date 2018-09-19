import io.snyk.plugin.client.ApiClient
import io.snyk.plugin.metrics.SegmentApi

object SendSegmentTestEvent extends App {
  val client = ApiClient.mock(null)
  val segment = SegmentApi(client)
  segment.track(
    "test event",
    Map(
      "a" -> 1,
      "b" -> 4.2,
      "c" -> 0.01d,
      "d" -> "foo"
    )
  )


}
