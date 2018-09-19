import io.snyk.plugin.client.{ApiClient, SnykConfig}

object TestUserInfoEndpoint extends App {
  val config = SnykConfig.default
  val client = ApiClient.standard(config)

  println(client.userInfo())
}
