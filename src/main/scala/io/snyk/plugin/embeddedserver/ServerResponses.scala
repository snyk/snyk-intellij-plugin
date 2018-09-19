package io.snyk.plugin.embeddedserver

import com.intellij.ide.BrowserUtil
import fi.iki.elonen.NanoHTTPD.{MIME_HTML, Response, newFixedLengthResponse}
import io.circe.Encoder
import io.circe.syntax._
import io.snyk.plugin.client.SnykConfig
import io.snyk.plugin.datamodel.SnykVulnResponse

import scala.concurrent.Future
import scala.util.{Failure, Success}
import scala.concurrent.ExecutionContext.Implicits.global

trait ServerResponses { self: MiniServer =>

  def notFoundResponse(path: String): Response =
    newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", s"Not Found: $path")

  def jsonResponse[T : Encoder](body: T): Response =
    newFixedLengthResponse(Response.Status.OK, "application/json", body.asJson.spaces4)

  def redirectTo(url: String): Response = {
    log.debug(s"Redirecting to $url")
    val r = newFixedLengthResponse(Response.Status.REDIRECT, MIME_HTML, "")
    r.addHeader("Location", url)
    r
  }

  def defaultFailureHandler(x: Throwable, params: ParamSet): Unit = {
    log.warn(x)
    log.debug(s"async task failed, redirecting to error page")
    navigateTo("/error", params.plus("errmsg" -> x.getMessage))
  }
  /**
    * Initiate an asynchronous security scan... once complete, navigate to the supplied URL
    */
  def asyncScanAndRedirectTo(
    successPath: String,
    params: ParamSet,
    onError: (Throwable, ParamSet) => Unit = defaultFailureHandler
  ): Future[SnykVulnResponse] = {
    log.debug(s"triggered async scan, will redirect to $successPath with params $params")
    pluginState.performScan() andThen {
      case Success(_) =>
        log.debug(s"async scan success, redirecting to $successPath with params $params")
        navigateTo(successPath, params)
      case Failure(x) => onError(x, params)
    }
  }


  /**
    * Initiate an asynchronous authorisation... once complete, navigate to the supplied URL
    */
  def asyncAuthAndRedirectTo(
    successPath: String,
    params: ParamSet,
    onError: (Throwable, ParamSet) => Unit = defaultFailureHandler
  ): Future[SnykConfig] = {
    if(pluginState.config.get.isSuccess) {
      Future fromTry pluginState.config.get
    } else {
      SnykConfig.auth(openBrowserFn = BrowserUtil.browse) transform { tryConfig =>
        tryConfig flatMap { config =>
          import self.pluginState.segmentApi
          log.debug(s"auth completed with $config, redirecting to $successPath with params $params")
          pluginState.config := tryConfig
          segmentApi.identify()
          segmentApi.track("login via IntelliJ")
          config.writeToFile().map(_ => config)
        }
      } andThen {
        case Failure(x) =>
          onError(x, params)
        case s @ Success(config) =>
          log.debug(s"navigating to $successPath with config updated")
          navigateTo(successPath, params)
      }
    }
  }

}
