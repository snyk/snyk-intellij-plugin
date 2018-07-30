package io.snyk.plugin.embeddedserver

object MimeType {
  private def extOnly(name: String): String = name.split("""\.""").last

  def of(name: String): String = extOnly(name) match {
    case "hbs"  => "text/html"
    case "htm"  => "text/html"
    case "html" => "text/html"
    case "xml"  => "application/xml"
    case "js"   => "application/x-javascript"
    case "json" => "application/json"
    case "png"  => "image/png"
    case "jpg"  => "image/jpeg"
    case "jpeg" => "image/jpeg"
    case "gif"  => "image/gif"
    case "svg"  => "image/svg+xml"
    case "mov"  => "video/quicktime"
    case "flv"  => "video/x-flv"
    case "mp4"  => "video/mp4"
    case "css"  => "text/css"
    case _      => "text/plain"
  }
}
