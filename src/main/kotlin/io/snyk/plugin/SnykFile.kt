package io.snyk.plugin

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Iconable
import com.intellij.openapi.vfs.VirtualFile
import snyk.common.RelativePathHelper
import javax.swing.Icon

data class SnykFile(val project: Project, val virtualFile: VirtualFile) {
    val relativePath = RelativePathHelper().getRelativePath(virtualFile, project)
}

fun toSnykFileSet(project: Project, virtualFiles: Set<VirtualFile>) =
    virtualFiles.map { SnykFile(project, it) }.toSet()
