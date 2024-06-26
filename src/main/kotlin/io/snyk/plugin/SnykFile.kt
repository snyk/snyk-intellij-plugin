package io.snyk.plugin

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Iconable
import com.intellij.openapi.vfs.VirtualFile
import snyk.common.RelativePathHelper
import javax.swing.Icon

data class SnykFile(val project: Project, val virtualFile: VirtualFile) {
    var icon: Icon? = null
    val relativePath = RelativePathHelper().getRelativePath(virtualFile, project)
    init {
        ApplicationManager.getApplication().runReadAction {
            virtualFile.getPsiFile(project)?.getIcon(Iconable.ICON_FLAG_READ_STATUS)
        }
    }
}

fun toSnykFileSet(project: Project, virtualFiles: Set<VirtualFile>) =
    virtualFiles.map { SnykFile(project, it) }.toSet()
