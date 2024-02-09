package snyk.trust

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
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
