package io.snyk.plugin.urlproto.snykplugin

import com.intellij.openapi.project.Project
import java.net.URLStreamHandler
import java.net.URLStreamHandlerFactory

class HandlerFactory(private val project: Project): URLStreamHandlerFactory {
    override fun createURLStreamHandler(protocol: String) : URLStreamHandler? {
        return when(protocol) {
            "snykplugin" -> Handler(project)
            else -> null
        }

    }
}