package io.snyk.plugin.snykcode.core

import ai.deepcode.javaclient.core.HashContentUtilsBase
import com.intellij.openapi.util.Computable
import java.nio.file.Files

class HashContentUtils private constructor() : HashContentUtilsBase(
    PDU.instance
) {
    override fun doGetFileContent(file: Any): String {
        require(file is SnykCodeFile) { "File $file must be of type SnykCodeFile but is ${file.javaClass.name}" }
        return RunUtils.computeInReadActionInSmartMode(
            file.project,
            Computable {
                return@Computable try {
                    String(Files.readAllBytes(file.virtualFile.toNioPath()))
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
