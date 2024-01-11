package snyk.common

import com.intellij.ide.util.gotoByName.GotoFileCellRenderer
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

class RelativePathHelper {
    private var relPathCached: String? = null

    fun getRelativePath(virtualFile: VirtualFile, project: Project): String? {
        if (relPathCached == null) {
            ApplicationManager.getApplication().runReadAction {
                relPathCached = GotoFileCellRenderer.getRelativePath(virtualFile, project)
            }
        }
        return relPathCached
    }
}
