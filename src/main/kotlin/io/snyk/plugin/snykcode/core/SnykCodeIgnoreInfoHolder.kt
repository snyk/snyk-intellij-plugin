package io.snyk.plugin.snykcode.core

import ai.deepcode.javaclient.core.DeepCodeIgnoreInfoHolderBase
import com.intellij.openapi.project.Project
import io.snyk.plugin.SnykFile
import io.snyk.plugin.getSnykTaskQueueService
import io.snyk.plugin.ui.SnykBalloonNotificationHelper
import java.io.File

class SnykCodeIgnoreInfoHolder private constructor() : DeepCodeIgnoreInfoHolderBase(
    HashContentUtils.instance,
    PDU.instance,
    SCLogger.instance
) {

    fun cleanIgnoreFileCachesIfAffected(filesToCheck: Collection<SnykFile>) {
        val ignoreFilesChanged = getIgnoreFiles(filesToCheck)
        for (ignoreFile in ignoreFilesChanged) {
            remove_ignoreFileContent(ignoreFile)
            AnalysisData.instance.removeProjectFromCaches(ignoreFile.project)
        }
    }

    fun updateIgnoreFileCachesIfAffected(filesToCheck: Collection<SnykFile>) {
        val ignoreFilesChanged = getIgnoreFiles(filesToCheck)
        for (ignoreFile in ignoreFilesChanged) {
            getSnykTaskQueueService(ignoreFile.project)
                ?.scheduleRunnable(
                    "updating caches for: ${PDU.instance.getFilePath(ignoreFile)}"
                ) { progress -> update_ignoreFileContent(ignoreFile, progress) }
        }
    }

    fun createDcIgnoreIfNeeded(project: Project) {
        val prjBasePath = project.basePath
        val gitignore = File("$prjBasePath/.gitignore")
        val dcignore = File("$prjBasePath/.dcignore")
        if (!gitignore.exists()) {
            val genericDcIgnoreFileCreated = try {
                dcignore.createNewFile()
            } catch (e: Exception) {
                SCLogger.instance.logWarn("Failed to create generic .dcignore: $e")
                false
            }
            if (genericDcIgnoreFileCreated) {
                val fullDcIgnoreText = this.javaClass.classLoader.getResource("full.dcignore")
                    ?.readText()
                    ?: throw RuntimeException("full.dcignore can not be found in plugin's resources")
                dcignore.writeText(fullDcIgnoreText)
                SnykBalloonNotificationHelper.showInfo(
                    "We added generic .dcignore file to upload only project's source code.", project
                )
            }
        }
    }

    private fun getIgnoreFiles(filesToCheck: Collection<SnykFile>) =
        filesToCheck
            .filter { it.virtualFile.name == ".dcignore" || it.virtualFile.name == ".gitignore" }
            .filter { it.virtualFile.isValid }
            .distinct()

    companion object {
        val instance = SnykCodeIgnoreInfoHolder()
    }
}
