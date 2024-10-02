package io.snyk.plugin

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.concurrency.runAsync
import snyk.common.RelativePathHelper

data class SnykFile(val project: Project, val virtualFile: VirtualFile) {
    val relativePath = runAsync { RelativePathHelper().getRelativePath(virtualFile, project) }

    fun isInContent(): Boolean {
        return this.virtualFile.isInContent(project)
    }
}

fun toSnykFileSet(project: Project, virtualFiles: Set<VirtualFile>) =
    virtualFiles.map { SnykFile(project, it) }.toSet()
