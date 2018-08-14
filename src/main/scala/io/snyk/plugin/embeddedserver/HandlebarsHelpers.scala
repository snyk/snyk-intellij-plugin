package io.snyk.plugin.embeddedserver

import java.net.{URI, URLEncoder}
import java.time.ZonedDateTime
import java.util.UUID

import com.github.jknack.handlebars.{Handlebars, Helper, Options, Template}
import com.github.jknack.handlebars.helper.EachHelper

import scala.collection.JavaConverters._
import scala.collection.immutable.TreeMap
import scala.io.{Codec, Source}

object HandlebarsHelpers {

  def safeString(s: String): Handlebars.SafeString = new Handlebars.SafeString(s)

  def falsy(x: Any): Boolean = Handlebars.Utils.isEmpty(x)
  def truthy(x: Any): Boolean = !falsy(x)

  case class Input[T](
    value: T,
    opts: Options
  ) {
    def valueStr: String = Option(value).map(_.toString).getOrElse("")
    def isFalsy: Boolean = falsy(value)
    val params: Seq[Any] = opts.params.toList.filterNot(_ == null)
    def param(idx: Int): Option[Any] = params.lift(idx)
    def paramStr(idx: Int): String = param(idx).map(_.toString).getOrElse("")

    lazy val all: Seq[Any] = Option(valueStr) ++: params.map(_.toString)
    def apply(idx: Int): Option[Any] = all.lift(idx)

    def hash: Map[String, Any] = opts.hash.asScala.toMap
    def hashStrings: Map[String, String] = hash.mapValues(_.toString)

    def template: Template = opts.fn
    def fn(): CharSequence = opts.fn() // yes, these are different...
    def inverse(): CharSequence = opts.inverse()
    def render(bool: Boolean): CharSequence = if(bool) fn() else inverse()

    def log(): Unit = {
      val loc = opts.fn.filename() + " " + opts.fn.position().mkString("[",",","]")

      val parts = TreeMap(
        "in"          -> loc,
        "value"       -> value,
        "context"     -> opts.context,
        "parent"      -> opts.context.parent(),
        "tagType"     -> opts.tagType,
        "params"      -> params,
        "blockParams" -> opts.blockParams.asScala,
        "hash"        -> hash
      ) filter {
        case (k,v: Iterable[_]) if v.isEmpty => false
        case _ => true
      } mapValues {
        case v: String => "\"" + v + "\""
        case v => v
      } map {
        case (k,v) => s"  $k = $v"
      }

      println(s"${opts.helperName} helper\n" + parts.mkString("\n"))
    }


  }

  object VarArgInput{
    def unapplySeq[T](in: Input[T]): Option[Seq[Any]] = Some(in.all)
  }

  def debugWrapper[T](fn: Input[T] => Any): Input[T] => Any = in => {
    //in.log()
    val out = fn(in)
    //println(s"${in.opts.helperName} helper output [$out]")
    out
  }

  def any(fn: Input[AnyRef] => Any): Helper[AnyRef] =
    (v: AnyRef, opts: Options) => debugWrapper(fn)(Input(v, opts)).asInstanceOf[AnyRef]

  def string(fn: Input[String] => Any): Helper[String] =
    (v: String, opts: Options) => debugWrapper(fn)(Input(v, opts)).asInstanceOf[AnyRef]

  val noop: Helper[Any] = (v, opts) => ""

  val all: Map[String, Helper[_]] = Map(
    "test-hook" -> noop,
    "snykTestModuleLink" -> noop,
    "each" -> any {
      case Input(iter: Iterable[_], opts) => EachHelper.INSTANCE.apply(iter.asJava, opts)
      case Input(v, opts) => EachHelper.INSTANCE.apply(v, opts)
    },
    "splitList" -> string { in =>
      if(in.isFalsy) "" else in.valueStr.split(',').map(_.trim)
    },
    "concat" -> any { in =>
      in.all.mkString("")
    },
    "svg" -> string { in =>
      val res = s"WEB-INF/assets/vectors/${in.valueStr}.svg"
      val svgxml = Source.fromResource(res, getClass.getClassLoader)(Codec.UTF8).mkString
      val updatedSvgXml = in.hashStrings.foldLeft(svgxml){
        case (acc,(k,v)) => acc.replaceFirst(s"""$k="[0-9]+"""", s"""$k="$v"""")
      }
      safeString(updatedSvgXml)
    },
    "include" -> string { in =>
      val templateFullFilename = in.template.filename
      val templateRelativeFilename = "WEB-INF/" + templateFullFilename.split("/WEB-INF/").last

      val webInfUri = getClass.getClassLoader.getResource("WEB-INF").toURI

      val templateUri = webInfUri.resolve(templateRelativeFilename)
      val soughtRelative = in.valueStr
      val soughtUri = templateUri.resolve(soughtRelative)

      println(s"templateFullFilename: $templateFullFilename")
      println(s"templateRelativeFilename: $templateRelativeFilename")
      println(s"webInfUri: $webInfUri")
      println(s"templateUri: $templateUri")
      println(s"soughtRelative: $soughtRelative")
      println(s"soughtUri: $soughtUri")
      val srcText = Source.fromFile(soughtUri).mkString
      safeString(srcText)
    },
    "slugify" -> string { in =>
      if (in.isFalsy) ""
      else in.value.replaceAll("""[^\w\s]+""", "").replaceAll("""\s+""", "").toLowerCase
    },
    "if_any" -> any { in => in.render(in.all.exists(truthy)) },
    "if_all" -> any { in => in.render(in.all.forall(truthy)) },
    "if_eq" -> any { in => in.render(in.value == in.param(0)) },
    "unless_eq" -> any { in => in.render(in.value != in.param(0)) },
    "count" -> any {
      case Input(v: Iterable[_], opts) => v.size
      case _ => 0
    },
    "ifCond" -> any {
      case in @ VarArgInput(v1, "==",  v2) => in.render(v1.toString == v2.toString)
      case in @ VarArgInput(v1, "===", v2) => in.render(v1 == v2)
      case in @ VarArgInput(v1, "<",   v2) => in.render(v1.toString < v2.toString)
      case in @ VarArgInput(v1, "<=",  v2) => in.render(v1.toString <= v2.toString)
      case in @ VarArgInput(v1, ">",   v2) => in.render(v1.toString < v2.toString)
      case in @ VarArgInput(v1, ">=",  v2) => in.render(v1.toString <= v2.toString)
      case in @ VarArgInput(v1: Boolean, "&&", v2: Boolean) => in.render(v1 && v2)
      case in @ VarArgInput(v1: Boolean, "||", v2: Boolean) => in.render(v1 || v2)
      case in =>
        in.log()
        in.render(false)
    },
    "add_encoded_param" -> string { in =>
      val url = in(0).map(_.toString) getOrElse ""
      val newUrl = for {
        name <- in(1).map(_.toString)
        value <- in(2).map(_.toString)
        escValue = URLEncoder.encode(value, "UTF-8")
      } yield {
        if(url contains '?') s"$url&$name=$escValue" else s"$url?$name=$escValue"
      }
      newUrl getOrElse url
    },
    "encode" -> string { in => URLEncoder.encode(in.value, "UTF-8") },
    "upgradeAvailable" -> any { in => in.inverse() }, //TODO: determine properly from the upgrade path
    "firstNonFalse" -> any { in => in.all.find(truthy).get },
    "uuid" -> any { in => in.valueStr + UUID.randomUUID() },
    "var" -> any { in => in.opts.context.combine(in.valueStr, in.paramStr(0)); "" },
    "markdown" -> string { in => "markdown goes here" }, //TODO: Implement
    "relativeMoment" -> any { in => "relativeMoment"}, //TODO: Implement
    "datetime" -> any { in => "datetime"}, //TODO: Implement
    "nowstr" -> any { in => ZonedDateTime.now().toString },
    "trim" -> string { in => in.valueStr.trim }
  )

  def registerAllOn(hb: Handlebars): Unit = all.foreach{ case (k,v) => hb.registerHelper(k, v) }
}
