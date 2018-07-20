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

  val loader = new ClassPathTemplateLoader
  loader.setPrefix("/WEB-INF/")
  loader.setSuffix(".hbs")
  val handlebars = new Handlebars(loader)

  val rootMirror = ru.runtimeMirror(getClass.getClassLoader)

  def methodMirrorFor(context: AnyRef, name: String): Option[ru.MethodMirror] = {
    val meta = rootMirror.reflect(context)
    val optAccessor = meta.symbol.info.decls find { m =>
      //      println(s"testing $m as mirror for $name")
      m.isMethod && m.isPublic && m.name.toString == name
    }
    optAccessor.map(a => meta.reflectMethod(a.asMethod))
  }

  object ScalaMemberResolver extends ValueResolver {
    override def resolve(context: AnyRef, name: String): AnyRef = context match {
      case m: collection.Map[_,_] =>
        MapValueResolver.INSTANCE.resolve(m.asJava, name)
      case _ =>
        println(s"ScalaMemberResolver.resolve [$name] from [${context.getClass.getName}]")
        val optMM = methodMirrorFor(context, name)
        val ret = optMM.fold(ValueResolver.UNRESOLVED)(m => resolve(m.apply())): AnyRef
        println(s"...returning ${ret.toString}")
        ret
    }

    override def resolve(context: scala.Any): AnyRef = context match {
      case m: collection.Map[_,_] =>
        MapValueResolver.INSTANCE.resolve(m.asJava)
      case _ =>
        println(s"ScalaMemberResolver.resolve context: [${context.getClass.getName}]")
        (context match {
          case Some(x) => x
          case None => null
          case x => x
        }).asInstanceOf[AnyRef]
    }

    override def propertySet(context: scala.Any): ju.Set[ju.Map.Entry[String, AnyRef]] = context match {
      case m: collection.Map[_,_] =>
        MapValueResolver.INSTANCE.propertySet(m.asJava)
      case _ =>
        println(s"ScalaMemberResolver.propertySet in context: [${context.getClass.getName}]")
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

  handlebars.registerHelper("each", ScalaEachHelper)
  handlebars.infiniteLoops(true)

  def mkContext(props: Map[String, AnyRef]) = Context
    .newBuilder(props)
    .resolver(
      ScalaMemberResolver,
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
