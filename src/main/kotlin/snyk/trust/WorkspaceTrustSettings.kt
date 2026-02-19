package snyk.trust

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.annotations.OptionTag
import java.util.Collections

@Service
@State(
  name = "Workspace.Trust.Settings",
  storages = [Storage("snyk.settings.xml", roamingType = RoamingType.DISABLED)],
)
class WorkspaceTrustSettings :
  SimplePersistentStateComponent<WorkspaceTrustSettings.State>(State()) {
  class State : BaseState() {
    @get:OptionTag("TRUSTED_PATHS") var trustedPaths by list<String>()
  }

  fun addTrustedPath(path: String) {
    state.trustedPaths.add(path)
  }

  fun removeTrustedPath(path: String) {
    state.trustedPaths.remove(path)
  }

  fun getTrustedPaths(): List<String> = Collections.unmodifiableList(state.trustedPaths)
}
