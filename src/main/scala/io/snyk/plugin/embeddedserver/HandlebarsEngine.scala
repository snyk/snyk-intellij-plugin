package io.snyk.plugin.embeddedserver

import java.{util => ju}

import com.github.jknack.handlebars.context.{JavaBeanValueResolver, MapValueResolver, MethodValueResolver}
import com.github.jknack.handlebars._
import com.github.jknack.handlebars.helper.EachHelper
import com.github.jknack.handlebars.io.ClassPathTemplateLoader

import scala.reflect.runtime.{universe => ru}
import scala.util.Try
import scala.collection.JavaConverters._

class HandlebarsEngine {

  private[this] val rootMirror: ru.Mirror = ru.runtimeMirror(getClass.getClassLoader)

  private[this] val loader = new ClassPathTemplateLoader("/WEB-INF/", ".hbs")
  private[this] val handlebars = new Handlebars(loader)
  handlebars.registerHelper("each", ScalaEachHelper)
  handlebars.infiniteLoops(true)

  def methodMirrorFor(context: AnyRef, name: String): Option[ru.MethodMirror] = {
    val meta = rootMirror.reflect(context)
    meta.symbol.info.decls
      .find(mem => mem.isMethod && mem.isPublic && mem.name.toString == name)
      .map(mem => meta.reflectMethod(mem.asMethod))
  }

  object ScalaResolver extends ValueResolver {
    override def resolve(context: AnyRef, name: String): AnyRef = context match {
      case m: collection.Map[_,_] => MapValueResolver.INSTANCE.resolve(m.asJava, name)
      case _ => methodMirrorFor(context, name) match {
        case None => ValueResolver.UNRESOLVED
        case Some(mm) => resolve(mm.apply())
      }
    }

    override def resolve(context: scala.Any): AnyRef = context match {
      case m: collection.Map[_,_] => MapValueResolver.INSTANCE.resolve(m.asJava)
      case Some(x: AnyRef) => x
      case None => null
      case x: AnyRef => x
    }

    override def propertySet(context: scala.Any): ju.Set[ju.Map.Entry[String, AnyRef]] = context match {
      case m: collection.Map[_,_] =>
        MapValueResolver.INSTANCE.propertySet(m.asJava)
      case _ =>
        val meta = rootMirror.reflect(context)
        val accessors = meta.symbol.info.decls.filter(m => m.isMethod && m.isPublic).toSeq
        val results = for {
          a <- accessors
          v <- Try(meta.reflectMethod(a.asMethod).apply()).toOption
        } yield a.name.toString -> v.asInstanceOf[AnyRef]
        results.toMap.asJava.entrySet
    }
  }

  object ScalaEachHelper extends Helper[AnyRef] {
    override def apply(context: scala.AnyRef, options: Options): AnyRef = context match {
      case iter: Iterable[_] => EachHelper.INSTANCE.apply(iter.asJava, options)
      case _ => EachHelper.INSTANCE.apply(context, options)
    }
  }

  def mkContext(props: Map[String, AnyRef]) = Context
    .newBuilder(props)
    .resolver(
      ScalaResolver,
      MapValueResolver.INSTANCE,
      MethodValueResolver.INSTANCE,
      JavaBeanValueResolver.INSTANCE,
    ).build()

  def render(fullPath: String, props: Map[String, AnyRef]): String = {
    val path = fullPath.dropRight(4)
    println(s"HandlebarsEngine rendering $path")
    val template = handlebars.compile(path)
    template(mkContext(props))
  }

  def render(path: String, props: (String, AnyRef)*): String = render(path, props.toMap)
}
