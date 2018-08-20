package io.snyk.plugin.embeddedserver

import java.net.URI

import fi.iki.elonen.NanoHTTPD.Response
import cats.implicits._

object HttpRouter {
  sealed private trait PatternPart {
    def bind(parts: Seq[String], params: ParamSet): Option[ParamSet]
  }
  private case class LiteralPatternPart(literal: String) extends PatternPart {
    override def bind(parts: Seq[String], params: ParamSet): Option[ParamSet] =
      if(parts.head == literal) Some(params) else None
  }
  private case class BindingPatternPart(varname: String) extends PatternPart {
    override def bind(parts: Seq[String], params: ParamSet): Option[ParamSet] =
      Some(params.plus(varname -> parts.head))
  }
  private case object WildcardPatternPart extends PatternPart {
    override def bind(parts: Seq[String], params: ParamSet): Option[ParamSet] =
      Some(params.plus("pathWildcard" -> parts.mkString("/")))
  }

  case class Pattern(raw: String) {
    private val rootPatternParts = raw.split('/').toSeq map {
      case s if s startsWith ":" => BindingPatternPart(s.tail)
      case s if s startsWith "*" => WildcardPatternPart
      case s => LiteralPatternPart(s)
    }

    def bind(initialPathParts: Seq[String], initialParams: ParamSet): Option[ParamSet] = {
      def loop(
        params: ParamSet,
        pathParts: Seq[String],
        patternParts: Seq[PatternPart]
      ): Option[ParamSet] = patternParts match {
        case WildcardPatternPart +: t =>
          WildcardPatternPart.bind(pathParts, params)
        case patternPart +: t =>
          patternPart.bind(pathParts, params) flatMap { loop(_, pathParts.tail, patternParts.tail)}
        case _ => params.some
      }
      loop(initialParams, initialPathParts, rootPatternParts)
    }

    override def toString: String = {
      rootPatternParts.map{
        case LiteralPatternPart(lit) => s"[$lit]"
        case BindingPatternPart(varname) => s"[:$varname]"
        case WildcardPatternPart => "[*]"
      }.mkString("/")
    }
  }

  def apply(entries: (String, Processor)*): HttpRouter = new HttpRouter(entries.toMap)
}

import HttpRouter._

class HttpRouter(entries: Map[String, Processor]) {

  private val patternedEntries = entries map { case (k,fn) => Pattern(k) -> fn }

  def route(uri: URI, params: ParamSet): Option[Response] = {
    val path = uri.getPath
    val pathParts = path.split('?').head.split('/').toSeq
    val pathPartsStr = pathParts.map(x => s"[$x]").mkString("/")

    patternedEntries.foldLeft(None: Option[Response]){
      case (acc, (pattern, fn)) =>
        println(s"testing route $pathPartsStr --> $pattern")
        acc orElse pattern.bind(pathParts, params).map(fn(path)(_))
    }
  }

}
