package io.snyk.plugin.snykcode.core

import ai.deepcode.javaclient.core.DeepCodeUtilsBase
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.Computable
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager

class SnykCodeUtils private constructor() : DeepCodeUtilsBase(
    AnalysisData.instance,
    SnykCodeParams.instance,
    SnykCodeIgnoreInfoHolder.instance,
    PDU.instance,
    SCLogger.instance
) {
    private val scLogger: SCLogger = SCLogger.instance

    fun allProjectFilesCount(project: Project): Int = allProjectFiles(project).size

    override fun allProjectFiles(projectO: Any): Collection<Any> {
        scLogger.logInfo("allProjectFiles requested")
        val project = PDU.toProject(projectO)
        val progressIndicator = ProgressManager.getInstance().progressIndicator ?: null
        val allVirtualFiles = mutableSetOf<VirtualFile>()

        val cancelled = !ProjectRootManager.getInstance(project).fileIndex.iterateContent {
            if (!it.isDirectory) {
                allVirtualFiles.add(it)
            }
            return@iterateContent progressIndicator?.isCanceled != true
        }
        if (cancelled) {
            allVirtualFiles.clear()
        }
        scLogger.logInfo("allProjectFiles scan finished. Found ${allVirtualFiles.size} files")

        val psiManager = PsiManager.getInstance(project)

        return RunUtils.computeInReadActionInSmartMode(
            project,
            Computable { allVirtualFiles.mapNotNull(psiManager::findFile) }
        ) ?: emptyList<PsiFile>()
    }

    override fun getFileLength(file: Any): Long = PDU.toPsiFile(file).virtualFile.length

    override fun getFileExtention(file: Any): String = PDU.toPsiFile(file).virtualFile.extension ?: ""

    override fun isGitIgnoredExternalCheck(file: Any): Boolean {
        val psiFile = PDU.toPsiFile(file)
        val virtualFile = psiFile.virtualFile ?: return false
        return ChangeListManager.getInstance(psiFile.project).isIgnoredFile(virtualFile)
    }

    companion object {
        val instance = SnykCodeUtils()
    }
}
