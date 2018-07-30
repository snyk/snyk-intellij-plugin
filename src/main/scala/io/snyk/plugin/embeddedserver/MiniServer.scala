package io.snyk.plugin.embeddedserver

import java.net.{URI, URL}

import fi.iki.elonen.NanoHTTPD

import scala.util.{Failure, Success, Try}
import io.snyk.plugin.client.ApiClient
import io.snyk.plugin.model.{DepTreeProvider, SnykPluginState}
import ColorProvider.RichColor
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

/**
  * A low-impact embedded HTTP server, built on top of the NanoHTTPD engine.
  * Minimal routing to process `.hbs` files via handlebars.
  *
  * Special logic to show an interim animation if the requested URL needs a new scan,
  * then asynchronously show the requested URL once the scan is complete
  */
class MiniServer(
  pluginState: SnykPluginState,
  depTreeProvider: DepTreeProvider,
  colorProvider: ColorProvider,
  apiClient: ApiClient,
  port0: Int = 0
) extends NanoHTTPD(port0) { // 0 == first available port

  import NanoHTTPD._

  start(SOCKET_READ_TIMEOUT, false)
  val port: Int = this.getListeningPort

  val rootUrl = new URL(s"http://localhost:$port")
  println(s"Mini-server on $rootUrl \n")

  val handlebarsEngine = new HandlebarsEngine

  private def navigateTo(path: String, params: ParamSet): Unit = pluginState.navigateTo(path, params)

  override def serve(session: IHTTPSession): Response = {
    val uri = URI.create(session.getUri).normalize()
    val path = uri.getPath
    val params = ParamSet.from(session)

    val interstitial = params.first("interstitial") getOrElse "/assets/images/scanning.mp4"

    if(params.requires(Requirement.NewScan)) {
      //explicitly triggered
      asyncScanAndRedirectTo(path, params without Requirement.NewScan)
      route(interstitial, ParamSet.Empty)
    } else if(params.needsScanResult && pluginState.latestScanResult.get.isEmpty) {
      //needed scan results, don't yet have any
      asyncScanAndRedirectTo(path, params)
      route(interstitial, ParamSet.Empty)
    } else {
      route(path, params)
    }
  }

  /**
    * Initiate an asynchronous security scan... once complete, navigate to the supplied URL
    */
  def asyncScanAndRedirectTo(redirectPath: String, params: ParamSet): Future[Unit] = {
    Future {
      println(s"triggered async scan, will redirect to $redirectPath with params $params")
      apiClient.runOn(depTreeProvider.getDepTree()) match {
        case Failure(x) =>
          x.printStackTrace()
        case Success(result) =>
          pluginState setLatestScanResult result
          println(s"async scan completed, redirecting to $redirectPath with params $params")
          navigateTo(redirectPath, params)
      }
    }
  }

  /**
    * Pass the request on to the appropriate handler for video, handlebars template, or static file
    */
  def route(path: String, params: ParamSet): Response =
    if(path.endsWith(".hbs")) serveHandlebars(path, params)
    else if(path.endsWith(".mp4")) serveVideo(path)
    else serveStatic(path, params)

  def serveVideo(path: String): Response = {
    pluginState.showVideo(path)
    println(s"miniserver 'serving' video at $path")
    newFixedLengthResponse(Response.Status.OK, "text/plain", s"serving video at $path")
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

  def serveHandlebars(path: String, params: ParamSet): Response = {
    val mime = MimeType of path

    println(s"miniserver serving handlebars template http://localhost:$port$path as $mime")

    def depTreeRoot = depTreeProvider.getDepTree()
    def nakedDisplayRoot = depTreeRoot.toDisplayNode
    def latestScanResult = pluginState.latestScanResult.get

    println(s"params = $params")

    val ctxExtra: Option[(String, AnyRef)] =
      if(params.requires(Requirement.DepTree))
        Some("rootNode" -> nakedDisplayRoot)
      else if(params.requires(Requirement.DepTreeRandomAnnotated))
        Some("rootNode" -> nakedDisplayRoot.randomiseStatus)
      else if(params.requires(Requirement.DepTreeAnnotated))
        Some("rootNode" -> nakedDisplayRoot.performVulnAssociation(latestScanResult.miniVulns))
      else if(params.requires(Requirement.MiniVulns))
        Some("miniVulns" -> latestScanResult.miniVulns.sortBy(v => (v.severityRank, v.moduleName, v.id)))
      else if(params.requires(Requirement.FullVulns))
        Some("vulnerabilities" -> latestScanResult.vulnerabilities)
      else None


    val baseCtx = Map(
      "localhost" -> s"http://localhost:$port",
      "apiAvailable"  -> apiClient.isAvailable
    ) ++ colorProvider.toMap.mapValues(_.hexRepr)

    val ctx = baseCtx ++ ctxExtra


    val body = try { handlebarsEngine.render(path, ctx) } catch {
      case ex: Exception =>
        ex.printStackTrace()
        ex.toString
    }

    newFixedLengthResponse(Response.Status.OK, mime, body)
  }
}
