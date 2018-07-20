package io.snyk.plugin.embeddedserver

import java.net.{URI, URL}

import com.intellij.openapi.project.Project
import fi.iki.elonen.NanoHTTPD

import scala.util.Try
import io.snyk.plugin.EnrichedMethods.RichProject
import io.snyk.plugin.client.ApiClient
import io.snyk.plugin.model.SnykPluginState
import monix.execution.atomic.Atomic

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class MiniServer(project: Project, pluginState: Atomic[SnykPluginState]) extends NanoHTTPD(0) { // 0 == first available port

  import ServerUtils._
  import NanoHTTPD._


  start(SOCKET_READ_TIMEOUT, false)
  val port: Int = this.getListeningPort

  val rootUrl = new URL(s"http://localhost:$port")
  println(s"Mini-server on $rootUrl \n")

  val handlebarsEngine = new HandlebarsEngine

  override def serve(session: IHTTPSession): Response = {
    val uri = URI.create(session.getUri).normalize()
    val path = uri.getPath

    if(path.endsWith(".hbs")) serveHandlebars(uri, session)
    else serveStatic(uri, session)
  }

  def serveStatic(uri: URI, session: IHTTPSession): Response = {
    val path = uri.getPath
    val mime = mimeOf(uri.extension)

    println(s"miniserver serving static http://localhost:$port$path as $mime")

    val inStr = Try {
      getClass.getClassLoader.getResourceAsStream(s"WEB-INF/$path")
    }

    inStr.fold(
      ex => newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", ex.toString),
      is => newChunkedResponse(Response.Status.OK, mime, is)
    )
  }

  def serveHandlebars(uri: URI, session: IHTTPSession): Response = {
    val path = uri.getPath
    val mime = mimeOf(uri.extension)
    val params = ParamSet.from(session)

    println(s"miniserver serving handlebars template http://localhost:$port$path as $mime")

    val depTreeRoot = project.toDepNode
    val nakedDisplayRoot = depTreeRoot.toDisplayNode

    println(params)

    val displayTreeRoot = if(params.isTrue("runscan")) {
      ApiClient.runOn(depTreeRoot) match {
        case Left(x) =>
          x.printStackTrace()
          nakedDisplayRoot
        case Right(result) =>
          pluginState.transform (_ withLatestScanResult result)
          nakedDisplayRoot.performVulnAssociation(result.miniVulns)
      }
    } else if(params.isTrue("triggerScan") && params.first("redirect").isDefined) {
      Future {
        println("triggered async scan")
        ApiClient.runOn(depTreeRoot) match {
          case Left(x) =>
            x.printStackTrace()
          case Right(result) =>
            pluginState.transform(_ withLatestScanResult result)
            println(s"panel is ${pluginState.get.htmlPanel}")
            for {
              panel <- pluginState.get.htmlPanel
              redirect <- params.first("redirect")
            } {
              println(s"async scan completed, redirecting to $redirect")
              panel.navigateTo(redirect)
            }
        }
      }
      nakedDisplayRoot
    } else if(params.isTrue("showLatestScan")) {
      nakedDisplayRoot.performVulnAssociation(pluginState.get.latestScanResult.miniVulns)
    } else if(params.isTrue("randomise")) {
      nakedDisplayRoot.randomiseStatus
    } else {
      nakedDisplayRoot
    }

    val body = try {
      handlebarsEngine.render(
        path,
        "localhost" -> s"http://localhost:$port",
        "project" -> project,
        "pluginState" -> pluginState.get,
        "rootNode" -> displayTreeRoot
      )
    } catch {
      case ex: Exception =>
        ex.printStackTrace()
        ex.toString
    }

    newFixedLengthResponse(Response.Status.OK, mime, body)
  }
}
