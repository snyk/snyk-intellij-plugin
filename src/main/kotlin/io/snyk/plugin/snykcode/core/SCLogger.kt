package io.snyk.plugin.snykcode.core

import ai.deepcode.javaclient.core.DCLoggerBase
import com.intellij.openapi.diagnostic.Logger
import java.util.function.Consumer
import java.util.function.Supplier

class SCLogger private constructor() : DCLoggerBase(
    Supplier { Consumer { message: String? -> Logger.getInstance(PRESENTABLE_NAME).debug(message) } },
    Supplier { Consumer { message: String? -> Logger.getInstance(PRESENTABLE_NAME).warn(message) } },
    Supplier { Logger.getInstance(PRESENTABLE_NAME).isDebugEnabled },
    Supplier { true },
    "io.snyk.plugin",
    PRESENTABLE_NAME
) {
    override fun getExtraInfo(): String {
        //TODO
        return ""
    }

    companion object {
        val instance = SCLogger()
        const val PRESENTABLE_NAME = "Snyk Code"
    }
}
