package io.snyk.plugin.embeddedserver

import java.net.URI

import com.intellij.openapi.project.Project
import fi.iki.elonen.NanoHTTPD

import scala.util.Try
import io.snyk.plugin.EnrichedMethods.RichProject
import io.snyk.plugin.client.ApiClient


class MiniServer(project: Project) extends NanoHTTPD(0) { // 0 == first available port

  import ServerUtils._
  import NanoHTTPD._


  start(SOCKET_READ_TIMEOUT, false)
  val port: Int = this.getListeningPort

  println(s"Mini-server on http://localhost:$port/ \n")

  val handlebarsEngine = new HandlebarsEngine

  override def serve(session: IHTTPSession): Response = {
    //import scala.collection.JavaConverters._
    //val parms = session.getParameters.asScala

    val uri = URI.create(session.getUri).normalize()
    val path = uri.getPath

    //TODO: Migrate from thymeleaf to https://github.com/jknack/handlebars.java as used elsewhere by Snyk
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

    println(s"miniserver serving handlebars template http://localhost:$port$path as $mime")

    val depTreeRoot = project.toDepNode

    ApiClient.postDepTree(depTreeRoot)

    val body = try {
      handlebarsEngine.render(
        path,
        "localhost" -> s"http://localhost:$port",
        "project" -> project,
        "depTreeRoot" -> depTreeRoot
      )
    } catch {
      case ex: Exception =>
        ex.printStackTrace()
        ex.toString
    }

    newFixedLengthResponse(Response.Status.OK, mime, body)
  }
}
