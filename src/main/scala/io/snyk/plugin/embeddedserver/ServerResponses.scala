package io.snyk.plugin.embeddedserver

import com.intellij.ide.BrowserUtil
import fi.iki.elonen.NanoHTTPD.{MIME_HTML, Response, newFixedLengthResponse}
import io.snyk.plugin.client.SnykCredentials
import io.snyk.plugin.datamodel.SnykVulnResponse

import scala.concurrent.Future
import scala.util.{Failure, Success}
import scala.concurrent.ExecutionContext.Implicits.global

trait ServerResponses { self: MiniServer =>

  def notFoundResponse(path: String): Response =
    newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", s"Not Found: $path")

  def redirectTo(url: String): Response = {
    println(s"Redirecting to $url")
    val r = newFixedLengthResponse(Response.Status.REDIRECT, MIME_HTML, "")
    r.addHeader("Location", url)
    r
  }

  /**
    * Initiate an asynchronous security scan... once complete, navigate to the supplied URL
    */
  def asyncScanAndRedirectTo(
    successPath: String,
    failurePath: String,
    params: ParamSet
  ): Future[SnykVulnResponse] = {
    println(s"triggered async scan, will redirect to $successPath with params $params")
    pluginState.performScan() andThen {
      case Success(_) =>
        println(s"async scan success, redirecting to $successPath with params $params")
        navigateTo(successPath, params)
      case Failure(x) =>
        x.printStackTrace()
        println(s"async scan failed, redirecting to $failurePath with params $params")
        navigateTo(failurePath, params.plus("errmsg" -> x.toString))
    }
  }


  /**
    * Initiate an asynchronous authorisation... once complete, navigate to the supplied URL
    */
  def asyncAuthAndRedirectTo(
    successPath: String,
    failurePath: String,
    params: ParamSet
  ): Future[SnykCredentials] = {
    if(pluginState.credentials.get.isSuccess) {
      Future fromTry pluginState.credentials.get
    } else {
      SnykCredentials.auth(openBrowserFn = BrowserUtil.browse) andThen {
        case Failure(x) =>
          println(s"auth failed with $x")
          navigateTo(failurePath, params.plus("errmsg" -> x.toString))
        case s @ Success(creds) =>
          println(s"auth completed with $creds, redirecting to $successPath with params $params")
          pluginState.credentials := s
          creds.writeToFile()
          println(s"navigating to $successPath with credentials updated")
          navigateTo(successPath, params)
      }
    }
  }

}
