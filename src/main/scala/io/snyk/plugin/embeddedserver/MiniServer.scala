package io.snyk.plugin.embeddedserver

import java.net.{URI, URL}

import fi.iki.elonen.NanoHTTPD

import scala.util.{Failure, Success, Try}
import io.snyk.plugin.client.SnykCredentials
import io.snyk.plugin.datamodel.SnykVulnResponse
import ColorProvider.RichColor
import com.intellij.ide.BrowserUtil

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import HandlebarsEngine.RichTemplate
import io.snyk.plugin.ui.state.SnykPluginState

import scala.collection.JavaConverters._

/**
  * A low-impact embedded HTTP server, built on top of the NanoHTTPD engine.
  * Minimal routing to process `.hbs` files via handlebars.
  *
  * Special logic to show an interim animation if the requested URL needs a new scan,
  * then asynchronously show the requested URL once the scan is complete
  */
class MiniServer(
  pluginState: SnykPluginState,
  colorProvider: ColorProvider,
  port0: Int = 0
) extends NanoHTTPD(port0) { // 0 == first available port

  import NanoHTTPD._
  import pluginState.apiClient

  start(SOCKET_READ_TIMEOUT, false)
  val port: Int = this.getListeningPort

  val rootUrl = new URL(s"http://localhost:$port")
  println(s"Mini-server on $rootUrl \n")

  val handlebarsEngine = new HandlebarsEngine

  private def navigateTo(path: String, params: ParamSet): Unit = pluginState.navigator.navigateTo(path, params)

//  val defaultScanning = "/anim/scanning/scanning.hbs"
  /** The default URL to show when async scanning if an explicit `interstitial` page hasn't been requested **/
  val defaultScanning = "/html/scanning.hbs"

  /**
    * Core NanoHTTPD serving method; Parse params for our own needs, extract the URI, and delegate to our own `serve`
    */
  override def serve(session: IHTTPSession): Response = {
    println(s"miniserver serving $session")
    val uri = URI.create(session.getUri).normalize()
    val params = ParamSet.from(session)
    val path = uri.getPath
    println(s"miniserver routing $path")
    try {
      router.route(uri, params) getOrElse notFoundResponse(path)
    } catch { case ex: Exception =>
      ex.printStackTrace()
      newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", ex.toString)
    }
  }

  private lazy val router = HttpRouter(
    "/assets/*"              -> serveStatic,
    "/partials/*"            -> serveHandlebars,
    "/perform-login"         -> performLogin,
    "/vulnerabilities"       -> serveVulns,
    "/please-login"          -> simpleServeHandlebars,
    "/project-not-available" -> simpleServeHandlebars,
    "/scanning"              -> simpleServeHandlebars
  )

  /**
    * A tiny helper for handling /<path> from the template /html/<path>.hbs
    */
  def simpleServeHandlebars(path: String)(params: ParamSet): Response = serveHandlebars(s"/html${path}.hbs")(params)

  def serveStatic(path: String)(params: ParamSet): Response = {
    val mime = MimeType of path
    println(s"miniserver serving static http://localhost:$port$path as $mime")
    val conn = WebInf.instance.openConnection(path)
    newFixedLengthResponse(Response.Status.OK, mime, conn.getInputStream, conn.getContentLengthLong)
  }

  def requireProjectId(proc: Processor): Processor = {
    pluginState.safeProjectId match {
      case Some(_) => proc
      case None => _ => _ => redirectTo("/project-not-available")
    }
  }

  def performLogin(path: String)(params: ParamSet): Response = {
    asyncAuthAndRedirectTo("/vulnerabilities", "/vulnerabilities", params)
    serveHandlebars("/html/logging-in.hbs")(params)
  }

  def requireAuth(proc: Processor): Processor = {
    if(pluginState.credentials.get.isFailure) _ => _ => {
      println("No cred - redirecting to auth")
      redirectTo("/please-login")
    }
    else proc
  }

  def requireScan(proc: Processor): Processor = {
    url => {
      params => {
        pluginState.latestScanForSelectedProject match {
          case Some(_) =>
            proc(url)(params)
          case None =>
            println("Triggered async scan")
            asyncScanAndRedirectTo(
              successPath = url,
              failurePath = url,
              params = params
            )
            redirectTo("/scanning")
        }
      }
    }
  }

  def notFoundResponse(path: String): Response =
    newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", s"Not Found: $path")


  val serveVulns =
    requireAuth {
      requireProjectId {
        requireScan {
          url => serveHandlebars("/html/vulns.hbs")
        }
      }
    }

  def redirectTo(url: String): Response = {
    val r = newFixedLengthResponse(Response.Status.REDIRECT, MIME_HTML, "")
    r.addHeader("Location", url)
    r
  }

  def serveHandlebars(path: String)(params: ParamSet): Response = {
    println(s"miniserver serving handlebars template http://localhost:$port$path")
    println(s"params = $params")

    val template = handlebarsEngine.compile(path)
    val refParams = template.collectReferenceParameters().asScala
    def latestScanResult = pluginState.latestScanForSelectedProject getOrElse SnykVulnResponse.empty

    val ctx = Map.newBuilder[String, Any]

    ctx ++= params.contextMap
    ctx ++= colorProvider.toMap.mapValues(_.hexRepr)

    ctx += "projectIds" -> pluginState.rootProjectIds
    ctx += "miniVulns" -> latestScanResult.miniVulns.sortBy(_.spec)
    ctx += "vulnerabilities" -> latestScanResult.vulnerabilities

    //TODO: should these just be added to state?
    val paramFlags = params.all("flags").map(_.toLowerCase -> true).toMap
    ctx += "flags" -> (paramFlags ++ pluginState.flags.asStringMap)
    ctx += "localhost" -> s"http://localhost:$port"
    ctx += "apiAvailable" -> apiClient.isAvailable

    val body = template render ctx.result()
    newFixedLengthResponse(Response.Status.OK, "text/html", body)
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
      case Success(result) =>
        println(s"async scan success, redirecting to $successPath with params $params")
        navigateTo(successPath, params)
      case Failure(x) =>
        x.printStackTrace()
        println(s"async scan failed, redirecting to $failurePath with params $params")
        navigateTo(failurePath, params)
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
      SnykCredentials.auth(openBrowser = BrowserUtil.browse) andThen {
        case Failure(x) =>
          println(s"auth failed with $x")
          navigateTo(failurePath, params)
        case Success(creds) =>
          println(s"auth completed with $creds, redirecting to $successPath with params $params")
          pluginState.credentials := Success(creds)
          creds.writeToFile()
          navigateTo(successPath, params)
      }
    }
  }
}
