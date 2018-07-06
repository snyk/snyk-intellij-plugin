package io.snyk.plugin.urlproto.snykplugin

import com.intellij.openapi.project.Project
import io.snyk.plugin.dependencyTreeRoot
import org.thymeleaf.TemplateEngine
import org.thymeleaf.context.IContext
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver
import java.io.*
import java.net.URL
import java.net.URLConnection
import java.nio.charset.Charset
import java.util.*

class TemplateURLConnection(val project: Project, url: URL) : URLConnection(url) {

//    data class Headers(
//        val lastModified: Long = 0L,
//        val length: Long = 0L,
//        val contentType: String
//    )
//
//    internal var CONTENT_LENGTH = "content-length"
//    internal var CONTENT_TYPE = "content-type"
//    internal var TEXT_PLAIN = "text/plain"
//    internal var LAST_MODIFIED = "last-modified"

    companion object {
        private val templateResolver = ClassLoaderTemplateResolver(TemplateURLConnection::class.java.classLoader)
        private val templateEngine = TemplateEngine()

        init {
            templateResolver.prefix = "/WEB-INF/"
            templateEngine.setTemplateResolver(templateResolver)
        }
    }


    private lateinit var bodyBytes: ByteArray
    private var initializedHeaders: Boolean = false
    private val length: Int get() = bodyBytes.size

    @Throws(IOException::class)
    override fun connect() {

        if (!this.connected) {
            try {
                val depTreeRoot = project.dependencyTreeRoot()

                val props: Map<String, Any> = mapOf(
                    "project" to project,
                    "depTreeRoot" to depTreeRoot
                )

                val ctx: IContext = object: IContext {
                    override fun containsVariable(name: String?): Boolean = props.containsKey(name)
                    override fun getLocale(): Locale = Locale.getDefault()
                    override fun getVariable(name: String?): Any? = props[name]
                    override fun getVariableNames(): MutableSet<String> = props.keys.toMutableSet()
                }

                val body = templateEngine.process(url.path, ctx)
                bodyBytes = body.toByteArray(Charset.forName("UTF-8"))

            } catch (ex: IOException) { throw ex }

            this.connected = true
        }

    }



    private fun initializeHeaders() {
        try { this.connect() } catch (var4: IOException) { }

        if (!this.initializedHeaders) {
            this.initializedHeaders = true
        }
    }

    override fun getContentLength(): Int {
        this.initializeHeaders()
        return if (this.length > 2147483647L) -1 else this.length
    }

    override fun getContentLengthLong(): Long = contentLength as Long

    override fun getLastModified(): Long {
        this.initializeHeaders()
        return 0L
    }

    private val _inputStream: InputStream by lazy { ByteArrayInputStream(bodyBytes) }

    @Throws(IOException::class)
    override fun getInputStream(): InputStream = _inputStream
}