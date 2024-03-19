package snyk.trust

import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.ProjectLocator
import com.intellij.openapi.vfs.VirtualFile
import io.snyk.plugin.isSnykCodeLSEnabled
import org.jetbrains.io.LocalFileFinder
import snyk.common.lsp.LanguageServerWrapper
import java.nio.file.Path
import java.nio.file.Paths

private val LOG = logger<WorkspaceTrustService>()

@Service
class WorkspaceTrustService {

    val settings
        get() = service<WorkspaceTrustSettings>()

    fun addTrustedPath(path: Path) {
        LOG.debug("Adding trusted path: $path")
        if (settings.getTrustedPaths().contains(path.toString())) {
            LOG.debug("Path is already trusted: $path")
            return
        }
        settings.addTrustedPath(path.toString())

        val virtualFile = LocalFileFinder.findFile(path.toFile().absolutePath) ?: return
        addToLanguageServer(virtualFile)
    }

    private fun addToLanguageServer(virtualFile: VirtualFile) {
        if (!isSnykCodeLSEnabled()) return
        val wrapper = LanguageServerWrapper.getInstance()
        ProjectLocator.getInstance().guessProjectForFile(virtualFile)?.let {
            wrapper.addContentRoots(it)
            wrapper.sendScanCommand(it)
        }
    }

    fun isPathTrusted(path: Path): Boolean {
        LOG.debug("Verifying if path is trusted: $path")
        return settings.getTrustedPaths().asSequence().mapNotNull {
            try {
                Paths.get(it)
            } catch (e: Exception) {
                LOG.warn(e)
                null
            }
        }.any {
            LOG.debug("Checking if the $it is an ancestor $path")
            it.isAncestor(path)
        }
    }
}

internal fun Path.isAncestor(child: Path): Boolean = child.startsWith(this)
