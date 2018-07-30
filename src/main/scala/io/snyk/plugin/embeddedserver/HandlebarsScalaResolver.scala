package io.snyk.plugin.embeddedserver

import com.github.jknack.handlebars.ValueResolver
import com.github.jknack.handlebars.context.MapValueResolver

import scala.util.Try
import scala.reflect.runtime.{universe => ru}
import java.{util => ju}

import scala.collection.JavaConverters._

class HandlebarsScalaResolver extends ValueResolver {
  private[this] val rootMirror: ru.Mirror = ru.runtimeMirror(getClass.getClassLoader)

  def methodMirrorFor(context: AnyRef, name: String): Option[ru.MethodMirror] = {
    val meta = rootMirror.reflect(context)
    meta.symbol.info.decls
      .find(mem => mem.isMethod && mem.isPublic && mem.name.toString == name)
      .map(mem => meta.reflectMethod(mem.asMethod))
  }

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
