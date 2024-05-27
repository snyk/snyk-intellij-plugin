package io.snyk.plugin.snykcode.core

import ai.deepcode.javaclient.core.HashContentUtilsBase
import com.intellij.openapi.util.Computable
import io.snyk.plugin.SnykFile

class HashContentUtils private constructor() : HashContentUtilsBase(
    PDU.instance
) {
    override fun doGetFileContent(file: Any): String {
        require(file is SnykFile) { "File $file must be of type SnykCodeFile but is ${file.javaClass.name}" }
        return RunUtils.computeInReadActionInSmartMode(
            file.project,
            Computable {
                try {
                    String(file.virtualFile.contentsToByteArray())
                } catch (e: Exception) {
                    SCLogger.instance.logWarn("Couldn't read content of ${file.virtualFile.name}, ${e.message}")
                    ""
                }
            }) ?: ""
    }

    companion object {
        val instance = HashContentUtils()
    }
}
