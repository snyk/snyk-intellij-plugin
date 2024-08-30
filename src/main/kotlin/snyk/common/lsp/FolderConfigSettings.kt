package snyk.common.lsp

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
    name = "FolderConfig.Settings", storages = [Storage("snyk.settings.xml", roamingType = RoamingType.DISABLED)]
)
class FolderConfigSettings : SimplePersistentStateComponent<FolderConfigSettings.State>(State()) {
    class State : BaseState() {
        @get:OptionTag("TRUSTED_PATHS")
        var configs by map<String, FolderConfig>()
    }

    fun addFolderConfig(folderConfig: FolderConfig) {
        state.configs[folderConfig.folderPath] = folderConfig.copy()
    }

    fun getFolderConfigs(): Map<String, FolderConfig> = Collections.unmodifiableMap(state.configs)

    fun clear() = state.configs.clear()

    fun addAll(folderConfigs: List<FolderConfig>) = folderConfigs.forEach { addFolderConfig(it) }
}
