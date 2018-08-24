package io.snyk.plugin
package embeddedserver

import com.github.jknack.handlebars.context.{JavaBeanValueResolver, MapValueResolver, MethodValueResolver}
import com.github.jknack.handlebars.{Context, Handlebars, Template}
import com.github.jknack.handlebars.io.{ClassPathTemplateLoader, CompositeTemplateLoader, FileTemplateLoader, URLTemplateLoader}

import scala.util.Try

/**
  * Wrapper for an instance of the Handlebars template engine.  Injects our helpers,
  * the necessary template loaders for our structure, and the HandlebarsScalaResolver
  * to allow scala-native resolution of properties.
  */
class HandlebarsEngine extends IntellijLogging {
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
//  handlebars.prettyPrint(true)
  HandlebarsHelpers.registerAllOn(handlebars)

  def compile(fullPath: String): Try[Template] = Try {
    val path = fullPath.dropRight(4)
    log.debug(s"HandlebarsEngine compiling $path")
    handlebars.compile(path)
  }

//  def render(fullPath: String, props: Map[String, Any]): String = {
//    val path = fullPath.dropRight(4)
//    log.debug(s"HandlebarsEngine rendering $path")
//    val template = handlebars.compile(path)
//    template.collectReferenceParameters()
//    template(mkContext(props))
//  }

//  def render(path: String, props: (String, Any)*): String = render(path, props.toMap)
}

object HandlebarsEngine {
  private[this] val handlebarsScalaResolver = new HandlebarsScalaResolver

  private[this] def mkContext(props: Map[String, Any]): Context = Context
    .newBuilder(props)
    .resolver(
      handlebarsScalaResolver,
      MapValueResolver.INSTANCE,
      MethodValueResolver.INSTANCE,
      JavaBeanValueResolver.INSTANCE,
    ).build()

  implicit class RichTemplate(val template: Template) extends AnyVal {
    def render(props: Map[String, Any]): String = {
      template(mkContext(props))
    }
  }
}
