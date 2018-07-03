package io.snyk.plugin.urlproto.snykplugin

import com.intellij.openapi.project.Project
import java.net.URL
import java.net.URLConnection
import java.net.URLStreamHandler
import java.io.IOException
import org.thymeleaf.TemplateEngine
import org.thymeleaf.context.IContext
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver
import java.util.*
import java.io.InputStream

class Handler(private val project: Project, private val classLoader: ClassLoader) : URLStreamHandler() {

    constructor(project: Project) : this(project, Handler::class.java.classLoader)

    private val templateResolver = ClassLoaderTemplateResolver(classLoader)
    private val templateEngine = TemplateEngine()

    object ctx: IContext {
        override fun containsVariable(name: String?): Boolean = false
        override fun getLocale(): Locale = Locale.getDefault()
        override fun getVariable(name: String?): Any? = null
        override fun getVariableNames(): MutableSet<String> = mutableSetOf()
    }

    init {
        println("constructing io.snyk.plugin.urlproto.internal.handler")

        templateResolver.prefix = "/WEB-INF/"
        templateEngine.setTemplateResolver(templateResolver)
    }

    @Throws(IOException::class)
    override fun openConnection(url: URL): URLConnection {
        val path = url.host + if(url.path.isNullOrEmpty()) "" else url.path
        println("handler got path: $path")
        //            return null
        if(path.endsWith(".templ")) {
            return TemplateURLConnection(project, url)
        } else {
            val resourceUrl = classLoader.getResource("WEB-INF/$path")
            return resourceUrl.openConnection()
        }
    }

}