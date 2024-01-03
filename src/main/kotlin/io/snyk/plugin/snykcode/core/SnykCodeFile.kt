package io.snyk.plugin.snykcode.core

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import snyk.common.RelativePathHelper

data class SnykCodeFile(val project: Project, val virtualFile: VirtualFile) {
    fun getRelativePath(): String = RelativePathHelper().getRelativePath(virtualFile, project) ?: virtualFile.path
}
