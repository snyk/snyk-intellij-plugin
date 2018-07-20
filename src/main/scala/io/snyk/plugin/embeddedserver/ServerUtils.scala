package io.snyk.plugin.embeddedserver

import java.io.{BufferedReader, InputStream, InputStreamReader, Reader}
import java.net.URI
import java.util.stream.Collectors


object ServerUtils {
  implicit class RichUri(val uri: URI) extends AnyVal {
    def extension: String = uri.getPath.split("""\.""").last
  }

  def resourceFileAsStream(path: String): Option[InputStream] =
    Option(getClass.getClassLoader.getResourceAsStream(s"WEB-INF/$path"))

  def resourceFileAsReader(path: String): Option[BufferedReader] =
    resourceFileAsStream(path).map(str => new BufferedReader(new InputStreamReader(str)))

  def resourceFileAsString(path: String): Option[String] =
    resourceFileAsReader(path) map {
      _.lines.collect(Collectors joining System.lineSeparator)
    }

  def mimeOf(ext: String): String = ext match {
    case "hbs"  => "text/html"
    case "htm"  => "text/html"
    case "html" => "text/html"
    case "xml"  => "application/xml"
    case "js"   => "application/x-javascript"
    case "json" => "application/json"
    case "png"  => "image/png"
    case "jpg"  => "image/jpeg"
    case "jpeg" => "image/jpeg"
    case "css"  => "text/css"
    case _      => "text/plain"
  }
}
