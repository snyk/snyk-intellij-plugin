package io.snyk.plugin.snykcode.core

import ai.deepcode.javaclient.core.DeepCodeUtilsBase
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.Computable
import com.intellij.openapi.vcs.changes.ChangeListManager
import io.snyk.plugin.snykcode.codeRestApi

class SnykCodeUtils private constructor() : DeepCodeUtilsBase(
    AnalysisData.instance,
    SnykCodeParams.instance,
    SnykCodeIgnoreInfoHolder.instance,
    PDU.instance,
    SCLogger.instance,
    codeRestApi
) {
    private val scLogger: SCLogger = SCLogger.instance

    fun allProjectFilesCount(project: Project): Int = allProjectFiles(project).size

    override fun allProjectFiles(projectO: Any): Collection<Any> {
        scLogger.logInfo("allProjectFiles requested")
        val project = PDU.toProject(projectO)
        val progressIndicator = ProgressManager.getInstance().progressIndicator ?: null
        val files = mutableSetOf<SnykCodeFile>()

        val cancelled = !ProjectRootManager.getInstance(project).fileIndex.iterateContent {
            if (!it.isDirectory) {
                files.add(SnykCodeFile(project, it))
            }
            return@iterateContent progressIndicator?.isCanceled != true
        }
        if (cancelled) {
            files.clear()
        }
        scLogger.logInfo("allProjectFiles scan finished. Found ${files.size} files")

        return RunUtils.computeInReadActionInSmartMode(
            project,
            Computable { files.filter { it.virtualFile.isValid } }
        ) ?: emptyList<SnykCodeFile>()
    }

    override fun getFileLength(file: Any): Long = PDU.toVirtualFile(file).length

    override fun getFileExtention(file: Any): String = PDU.toVirtualFile(file).extension ?: ""

    override fun isGitIgnoredExternalCheck(file: Any): Boolean {
        require(file is SnykCodeFile) { "File $file must be of type SnykCodeFile but is ${file.javaClass.name}" }
        return ChangeListManager.getInstance(PDU.instance.getProject(file) as Project).isIgnoredFile(file.virtualFile)
    }

    companion object {
        val instance = SnykCodeUtils()
    }
}
