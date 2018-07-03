package io.snyk.plugin

import org.thymeleaf.TemplateEngine
import org.thymeleaf.templatemode.TemplateMode
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver

object Templates {
    val resolver = ClassLoaderTemplateResolver()
    val templateEngine = TemplateEngine()

    init {
        resolver.templateMode = TemplateMode.HTML
        resolver.prefix = "/WEB-INF/templates/"
        resolver.suffix = ".html"
        templateEngine.setTemplateResolver(resolver)
    }
}