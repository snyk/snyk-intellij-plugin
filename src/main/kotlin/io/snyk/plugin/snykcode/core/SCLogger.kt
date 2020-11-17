package io.snyk.plugin.snykcode.core

import ai.deepcode.javaclient.core.DCLoggerBase
import com.intellij.openapi.diagnostic.Logger
import java.util.function.Consumer
import java.util.function.Supplier

class SCLogger private constructor() : DCLoggerBase(
    Supplier { Consumer { message: String? -> Logger.getInstance("SnykCode").debug(message) } },
    Supplier { Consumer { message: String? -> Logger.getInstance("SnykCode").warn(message) } },
    Supplier { Logger.getInstance("SnykCode").isDebugEnabled },
    Supplier { true },
    "io.snyk.plugin"
) {
    override fun getExtraInfo(): String {
        //TODO
        return ""
    }

    companion object {
        val instance = SCLogger()
    }
}
