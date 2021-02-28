package io.snyk.plugin.snykcode.core

import ai.deepcode.javaclient.core.DeepCodeUtilsBase
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.util.Computable
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager

class SnykCodeUtils private constructor() : DeepCodeUtilsBase(
    AnalysisData.instance,
    SnykCodeParams.instance,
    SnykCodeIgnoreInfoHolder.instance,
    SCLogger.instance
) {
    private val scLogger: SCLogger = SCLogger.instance

    fun allProjectFilesCount(project: Project): Int = allProjectFiles(project).size

    override fun allProjectFiles(projectO: Any): Collection<Any> {
        val project = PDU.toProject(projectO)
        return RunUtils.computeInReadActionInSmartMode(
            project,
            Computable {
                val psiManager = PsiManager.getInstance(project)
                val projectDir = project.guessProjectDir()
                if (projectDir == null) {
                    scLogger.logWarn("Project directory not found for: $project")
                    return@Computable emptyList<Any>()
                }
                val prjDirectory = psiManager.findDirectory(projectDir)
                if (prjDirectory == null) {
                    scLogger.logWarn("Project directory not found for: $project")
                    return@Computable emptyList<Any>()
                }
                getFilesRecursively(prjDirectory)
            }) ?: emptyList()
    }

    private fun getFilesRecursively(psiDirectory: PsiDirectory): List<PsiFile> {
        val psiFileList: MutableList<PsiFile> = mutableListOf(*psiDirectory.files)
        for (subDir in psiDirectory.subdirectories) {
            psiFileList.addAll(getFilesRecursively(subDir))
        }
        return psiFileList
    }

    override fun getFileLength(file: Any): Long = PDU.toPsiFile(file).virtualFile.length

    override fun getFileExtention(file: Any): String = PDU.toPsiFile(file).virtualFile.extension ?: ""

    override fun isGitIgnored(file: Any): Boolean {
        val psiFile = PDU.toPsiFile(file)
        val virtualFile = psiFile.virtualFile ?: return false
        return ChangeListManager.getInstance(psiFile.project).isIgnoredFile(virtualFile)
    }

    companion object {
        val instance = SnykCodeUtils()
    }
}
