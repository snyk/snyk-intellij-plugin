package io.snyk.plugin.urlproto

import java.net.URLStreamHandler



object Registrator {

    fun registerHandler(handlerClass: Class<out URLStreamHandler>) {
        // Ensure that we are registered as a url protocol handler for JavaFxCss:/path css files.
        val was = System.getProperty("java.protocol.handler.pkgs", "")
        val pkg = handlerClass.getPackage().name
        val ind = pkg.lastIndexOf('.')
        assert(ind != -1) { "You can't add url handlers in the base package" }
        assert("Handler" == handlerClass.simpleName) { "A URLStreamHandler must be in a class named Handler; not " + handlerClass.simpleName }

        val protoName = handlerClass.getPackage().name.substring(0, ind)
        val newPkgs = protoName + if (was.isEmpty()) "" else "|$was"
        System.setProperty("java.protocol.handler.pkgs", newPkgs)

        println("registering $protoName as ${handlerClass.canonicalName}")
    }

}