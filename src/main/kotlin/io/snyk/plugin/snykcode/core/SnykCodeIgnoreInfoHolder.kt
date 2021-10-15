package io.snyk.plugin.snykcode.core

import ai.deepcode.javaclient.core.DeepCodeIgnoreInfoHolderBase
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import io.snyk.plugin.services.SnykTaskQueueService
import io.snyk.plugin.ui.SnykBalloonNotificationHelper
import java.io.File

class SnykCodeIgnoreInfoHolder private constructor() : DeepCodeIgnoreInfoHolderBase(
    HashContentUtils.instance,
    PDU.instance,
    SCLogger.instance
) {

    fun cleanIgnoreFileCachesIfAffected(project: Project, virtualFilesToCheck: Collection<VirtualFile>) {
        val ignoreFilesChanged = getIgnoreFiles(project, virtualFilesToCheck)
        for (ignoreFile in ignoreFilesChanged) {
            remove_ignoreFileContent(ignoreFile)
            AnalysisData.instance.removeProjectFromCaches(project)
        }
    }

    fun updateIgnoreFileCachesIfAffected(project: Project, virtualFilesToCheck: Collection<VirtualFile>) {
        val ignoreFilesChanged = getIgnoreFiles(project, virtualFilesToCheck)
        for (ignoreFile in ignoreFilesChanged) {
            project.service<SnykTaskQueueService>().scheduleRunnable(
                "updating caches for: ${PDU.instance.getFilePath(ignoreFile)}"
            ) { progress ->
                update_ignoreFileContent(ignoreFile, progress)
            }
        }
    }

    fun createDcIgnoreIfNeeded(project: Project) {
        val prjBasePath = project.basePath
        val gitignore = File("$prjBasePath/.gitignore")
        val dcignore = File("$prjBasePath/.dcignore")
        if (!gitignore.exists() && dcignore.createNewFile()) {
            val fullDcIgnoreText = this.javaClass.classLoader.getResource("full.dcignore")?.readText()
                ?: throw RuntimeException("full.dcignore can not be found in plugin's resources")
            dcignore.writeText(fullDcIgnoreText)
            SnykBalloonNotificationHelper.showInfo(
                "We added generic .dcignore file to upload only project's source code.",
                project
            )
        }
    }

    private fun getIgnoreFiles(project: Project, virtualFilesToCheck: Collection<VirtualFile>) =
        virtualFilesToCheck
            .filter { it.name == ".dcignore" || it.name == ".gitignore" }
            .distinct()
            .mapNotNull { PsiManager.getInstance(project).findFile(it) }

    companion object{
        val instance = SnykCodeIgnoreInfoHolder()
    }
}
