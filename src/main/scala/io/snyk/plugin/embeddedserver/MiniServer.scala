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
  val defaultScanning = "/assets/video/scanning.mp4"

  /**
    * Core NanoHTTPD serving method; Parse params for our own needs, extract the URI, and delegate to our own `serve`
    */
  override def serve(session: IHTTPSession): Response = {
    println(s"miniserver serving $session")
    route(
      uri = URI.create(session.getUri).normalize(),
      params = ParamSet.from(session)
    )
  }

  /**
    * Pass the request on to the appropriate handler for video, handlebars template, or static file
    */
  def route(uri: URI, params: ParamSet): Response = {
    val path = uri.getPath

    println(s"miniserver routing $path")

    // First, capture any params that should immediately be saved as state
    params.first("selectedProjectId").filterNot(_.isEmpty) foreach { id =>
      println(s"setting selected project: $id")
      pluginState.selectedProjectId := id
    }

    val rootIds = pluginState.rootProjectIds

    if(pluginState.selectedProjectId.get.isEmpty && rootIds.size <= 1) {
      val id = rootIds.headOption.getOrElse("")
      println(s"auto-setting selected project: [$id]")
      pluginState.selectedProjectId := id
    }

    if (path.endsWith(".hbs")) serveHandlebars(path, params)
    else if (path.endsWith(".mp4")) serveVideo(path)
    else serveStatic(path, params)
  }

  def serveVideo(path: String): Response = {
    println(s"miniserver 'serving' video at $path")
    pluginState.navigator.showVideo(path)
    newFixedLengthResponse(Response.Status.ACCEPTED, "text/plain", s"serving video at $path")
  }

  def serveStatic(path: String, params: ParamSet): Response = {
    val mime = MimeType of path
    println(s"miniserver serving static http://localhost:$port$path as $mime")
    Try {
      val conn = WebInf.instance.openConnection(path)
      newFixedLengthResponse(Response.Status.OK, mime, conn.getInputStream, conn.getContentLengthLong)
    }.fold(
      ex => newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", ex.toString),
      identity
    )
  }

  private[this] def paramsNeedingProjectId = Set("nakedRoot", "annotatedRoot", "miniVulns", "vulnerabilities")
  private[this] def paramsNeedingScan = Set("annotatedRoot", "miniVulns", "vulnerabilities")

  def redirectTo(url: String): Response = {
    val r = newFixedLengthResponse(Response.Status.REDIRECT, MIME_HTML, "")
    r.addHeader("Location", url)
    r
  }

  def serveHandlebars(path: String, params: ParamSet): Response = {
    val mime = MimeType of path

    println(s"miniserver serving handlebars template http://localhost:$port$path as $mime")

    def depTreeRoot = pluginState.depTree()


    println(s"params = $params")

    try {
      val template = handlebarsEngine.compile(path)
      val refParams = template.collectReferenceParameters().asScala

      val interstitial = params.first("interstitial") getOrElse defaultScanning

      val projId = pluginState.selectedProjectId.get
      def latestScanResult = pluginState.latestScanForSelectedProject getOrElse SnykVulnResponse.empty

      if(pluginState.credentials.get.isFailure && refParams.exists(paramsNeedingScan.contains)) {
        asyncAuthAndRedirectTo(
          successPath = path,
          failurePath = path,
          params = params
        )
        redirectTo(interstitial)
      } else if(projId.isEmpty && refParams.exists(paramsNeedingProjectId.contains)) {
        redirectTo("/html/select-project.hbs" + params.plus("redirectUrl" -> path).queryString )
      } else if(latestScanResult.isEmpty && refParams.exists(paramsNeedingScan.contains)) {
        asyncScanAndRedirectTo(
          successPath = path,
          failurePath = path,
          params = params
        )
        redirectTo(interstitial)
      } else {

        val ctx = Map.newBuilder[String, Any]

        ctx ++= params.contextMap
        ctx ++= colorProvider.toMap.mapValues(_.hexRepr)

        //TODO: figure a nicer syntax here
        refParams foreach {
          case id@"projectIds" => ctx += id -> pluginState.rootProjectIds
          case id@"miniVulns" => ctx += id -> latestScanResult.miniVulns.sortBy(_.spec)
          case id@"vulnerabilities" => ctx += id -> latestScanResult.vulnerabilities
          case p => println(s"Ref Param: $p")
        }

        //TODO: should these just be added to state?
        val paramFlags = params.all("flags").map(_.toLowerCase -> true).toMap
        ctx += "flags" -> (paramFlags ++ pluginState.flags.asStringMap)

        ctx += "localhost" -> s"http://localhost:$port"
        ctx += "apiAvailable" -> apiClient.isAvailable

        val body = template render ctx.result()
        newFixedLengthResponse(Response.Status.OK, mime, body)
      }

    } catch { case ex: Exception =>
      ex.printStackTrace()
      newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", ex.toString)
    }
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
