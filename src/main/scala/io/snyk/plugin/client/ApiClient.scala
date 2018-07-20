package io.snyk.plugin.client

import io.snyk.plugin.model.SnykMavenArtifact

object ApiClient {
  def postDepTree(treeRoot: SnykMavenArtifact): String = {
    //TODO: Get token and endpoint from ~/.config/configstore/snyk.json

    import com.softwaremill.sttp._

    val jsonReq = SnykClientSerialisation.encodeRoot(treeRoot).noSpaces
    println("Built JSON Request")
    println(jsonReq)

//    val apiEndpoint = "http://snyk.io/api"
//    val apiToken = "2979c2e5-019d-48fd-9f0b-895ec6e6a4d5"

    val apiEndpoint = "http://dev.snyk.io/api"
    val apiToken = "71e3aa89-03bc-4005-a050-727e68d762eb"

//    val apiEndpoint = "http://localhost:8000/api"
//    val apiToken = "e97cd45b-e011-4ac8-a898-dad07e47d736"

    val request = sttp.post(uri"$apiEndpoint/v1/vuln/maven")
      .header("Authorization", s"token $apiToken")
      .header("x-is-ci", "false")
      .header("content-type", "application/json")
      .header("user-agent", "Needle/2.1.1 (Node.js v8.11.3; linux x64)")
      .body(jsonReq)

    implicit val backend = HttpURLConnectionBackend()
    println("Sending...")
    val response = request.send()
    println("...Sent")

    val ret = response.unsafeBody
    println("Got Response")
    println(ret)

    ret
  }
}
