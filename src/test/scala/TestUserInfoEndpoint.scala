import io.snyk.plugin.client.{ApiClient, SnykCredentials}

object TestUserInfoEndpoint extends App {
  val creds = SnykCredentials.default
  val client = ApiClient.standard(creds)

  println(client.userInfo())
}
