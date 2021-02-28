package io.snyk.plugin.snykcode.core

import ai.deepcode.javaclient.core.DCLoggerBase
import com.intellij.openapi.diagnostic.Logger
import java.util.function.Consumer
import java.util.function.Supplier

class SCLogger private constructor() : DCLoggerBase(
    Supplier { Consumer { message: String? -> Logger.getInstance(presentableName).debug(message) } },
    Supplier { Consumer { message: String? -> Logger.getInstance(presentableName).warn(message) } },
    Supplier { Logger.getInstance(presentableName).isDebugEnabled },
    Supplier { true },
    "io.snyk.plugin",
    presentableName
) {
    override fun getExtraInfo(): String {
        //TODO
        return ""
    }

    companion object {
        val instance = SCLogger()
        const val presentableName = "SnykCode"
    }
}
