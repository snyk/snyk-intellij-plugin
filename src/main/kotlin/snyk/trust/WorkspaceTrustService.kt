package snyk.trust

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import java.nio.file.Path

private val LOG = logger<WorkspaceTrustService>()

@Service(Service.Level.APP)
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

  fun removeTrustedPath(path: Path) {
    LOG.debug("Removing trusted path: $path")
    settings.removeTrustedPath(path.toString())
  }
}
