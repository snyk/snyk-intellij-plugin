package io.snyk.plugin.snykcode.core

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Iconable
import com.intellij.openapi.vfs.VirtualFile
import io.snyk.plugin.getPsiFile
import snyk.common.RelativePathHelper
import javax.swing.Icon

data class SnykCodeFile(val project: Project, val virtualFile: VirtualFile) {
    var icon: Icon? = null
    val relativePath = RelativePathHelper().getRelativePath(virtualFile, project)
    init {
        ApplicationManager.getApplication().runReadAction {
            virtualFile.getPsiFile(project)?.getIcon(Iconable.ICON_FLAG_READ_STATUS)
        }
    }
}
