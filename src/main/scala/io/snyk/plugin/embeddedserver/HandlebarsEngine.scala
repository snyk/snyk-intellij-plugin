package io.snyk.plugin.embeddedserver

import com.github.jknack.handlebars.context.{JavaBeanValueResolver, MapValueResolver, MethodValueResolver}
import com.github.jknack.handlebars._
import com.github.jknack.handlebars.helper.EachHelper
import com.github.jknack.handlebars.io.{ClassPathTemplateLoader, CompositeTemplateLoader, FileTemplateLoader, URLTemplateLoader}

import scala.collection.JavaConverters._

/**
  * Wrapper for an instance of the Handlebars template engine.  Injects our helpers,
  * the necessary template loaders for our structure, and the HandlebarsScalaResolver
  * to allow scala-native resolution of properties.
  */
class HandlebarsEngine {
  val webInfPath = getClass.getClassLoader.getResource("WEB-INF")

  def loader(prefix: String, suffix: String): URLTemplateLoader = WebInf.instance match {
    case wi: FileBasedWebInf => new FileTemplateLoader(wi.resolvePath(prefix), suffix)
    case wi: JarBasedWebInf => new ClassPathTemplateLoader(s"/WEB-INF/$prefix/", suffix)
  }

  private[this] val handlebars = new Handlebars(
    new CompositeTemplateLoader(
      loader("/", ".hbs"),
      loader("/components", ".html"),
      loader("/partials", ".hbs")
    )
  )

  handlebars.infiniteLoops(true)
  HandlebarsHelpers.registerAllOn(handlebars)

  val handlebarsScalaResolver = new HandlebarsScalaResolver

  private[this] def mkContext(props: Map[String, Any]): Context = Context
    .newBuilder(props)
    .resolver(
      handlebarsScalaResolver,
      MapValueResolver.INSTANCE,
      MethodValueResolver.INSTANCE,
      JavaBeanValueResolver.INSTANCE,
    ).build()

  def render(fullPath: String, props: Map[String, Any]): String = {
    val path = fullPath.dropRight(4)
    println(s"HandlebarsEngine rendering $path")
    val template = handlebars.compile(path)
    template(mkContext(props))
  }

  def render(path: String, props: (String, Any)*): String = render(path, props.toMap)
}
