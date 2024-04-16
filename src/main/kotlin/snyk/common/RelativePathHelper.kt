package snyk.common

import com.intellij.ide.util.gotoByName.GotoFileCellRenderer
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

class RelativePathHelper {
    private var relPathCached: String? = null

    fun getRelativePath(virtualFile: VirtualFile, project: Project): String? {
        if (relPathCached == null) {
            DumbService.getInstance(project).runReadActionInSmartMode {
                relPathCached = GotoFileCellRenderer.getRelativePath(virtualFile, project)
            }
        }
        return relPathCached
    }
}
