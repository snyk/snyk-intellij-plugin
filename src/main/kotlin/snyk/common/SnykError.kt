package snyk.common

import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile

data class SnykError(
    val message: String,
    val path: String,
    val code: Int? = null
) {
    var virtualFile: VirtualFile? = null

    init {
        if (path.isNotEmpty()) {
            try {
                virtualFile = LocalFileSystem.getInstance().findFileByPath(this.path)
            } catch (ignore: RuntimeException) {
                // ignore because this file is optional
            }
        }
    }
}
