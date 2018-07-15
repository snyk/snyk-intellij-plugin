package io.snyk.plugin

import java.net.URI

import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.Response
import fi.iki.elonen.NanoHTTPD.IHTTPSession
import org.thymeleaf.TemplateEngine
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver
import com.intellij.openapi.project.Project
import RichProject.Adaptor
import org.thymeleaf.context.IContext

import scala.collection.JavaConverters._
import java.{util => ju}

import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.stream.Collectors
import scala.util.Try

object MiniServer {
  implicit class RichUri(val uri: URI) extends AnyVal {
    def extension: String = uri.getPath.split("""\.""").last
  }

  def resourceFileAsString(path: String): Option[String] = {
    val is = getClass.getClassLoader.getResourceAsStream(s"WEB-INF/$path")
    if (is != null) {
      val reader = new BufferedReader(new InputStreamReader(is))
      Some(reader.lines.collect(Collectors.joining(System.lineSeparator)))
    } else None
  }

  def mimeOf(ext: String): String = ext match {
    case "htm"  => "text/html"
    case "html" => "text/html"
    case "xml"  => "application/xml"
    case "js"   => "application/x-javascript"
    case "json" => "application/x-javascript"
    case "png"  => "image/png"
    case "jpg"  => "image/jpeg"
    case "jpeg" => "image/jpeg"
    case "css"  => "text/css"
    case _      => "text/plain"
  }

}
class MiniServer(project: Project) extends NanoHTTPD(0) {

  import MiniServer._

  val classLoader = this.getClass.getClassLoader
  private val templateResolver = new ClassLoaderTemplateResolver(classLoader)
  private val templateEngine = new TemplateEngine()

  templateResolver.setPrefix("/WEB-INF/")
  templateEngine.setTemplateResolver(templateResolver)


  start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
  val port = this.getListeningPort
  println(s"Mini-server on http://localhost:$port/ \n")



  override def serve(session: IHTTPSession): Response = {
    val parms = session.getParameters.asScala

    val uri = URI.create(session.getUri).normalize()
    val path = uri.getPath

    if(path.endsWith("_templ")) serveTemplate(uri, session)
    else serveStatic(uri, session)
  }

  def serveStatic(uri: URI, session: IHTTPSession): Response = {
    val path = uri.getPath
    val mime = mimeOf(uri.extension)

    println(s"miniserver serving static $path as $mime")

    val inStr = Try {
      getClass.getClassLoader.getResourceAsStream(s"WEB-INF/$path")
    }

    inStr.fold(
      ex => NanoHTTPD.newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", ex.toString),
      is => NanoHTTPD.newChunkedResponse(Response.Status.OK, mime, is)
    )
  }

  def serveTemplate(uri: URI, session: IHTTPSession): Response = {
    val path = uri.getPath
    val ext = uri.extension.dropRight(6)
    val mime = mimeOf(ext)

    println(s"miniserver serving template $path ext $ext as $mime")

    val depTreeRoot = project.toDepNode

    println(depTreeRoot.toJsonString())

    val props = Map(
      "localhost" -> s"http://localhost:$port",
      "project" -> project,
      "depTreeRoot" -> depTreeRoot.javaForm
    )

    val ctx: IContext = new IContext {
      override def containsVariable(name: String): Boolean = props.contains(name)
      override def getLocale(): ju.Locale = ju.Locale.getDefault()
      override def getVariable(name: String): AnyRef = props(name)
      override def getVariableNames(): ju.Set[String] = props.keySet.asJava
    }

    val body = templateEngine.process(path, ctx)

    NanoHTTPD.newFixedLengthResponse(Response.Status.OK, mime, body)
  }
}
