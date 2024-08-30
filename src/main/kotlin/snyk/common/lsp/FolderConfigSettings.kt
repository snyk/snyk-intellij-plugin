package snyk.common.lsp

import com.google.gson.Gson
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.annotations.MapAnnotation

@Service
@State(
    name = "FolderConfig.Settings", storages = [Storage("snyk.settings.xml", roamingType = RoamingType.DISABLED)]
)
class FolderConfigSettings : SimplePersistentStateComponent<FolderConfigSettings.State>(State()) {
    val gson = Gson()

    class State : BaseState() {
        @get:MapAnnotation(keyAttributeName = "folderPath", entryTagName = "folderConfig")
        var configs by map<String, String>()
    }

    private fun addFolderConfig(folderConfig: FolderConfig) {
        state.configs[folderConfig.folderPath] = gson.toJson(folderConfig)
    }

    fun getFolderConfig(folderPath: String): FolderConfig? {
        return gson.fromJson(state.configs[folderPath], FolderConfig::class.java)
    }

    fun getAll() : Map<String, FolderConfig> {
        return state.configs.map {
            it.key to gson.fromJson(it.value, FolderConfig::class.java)
        }.toMap()
    }

    fun clear() = state.configs.clear()

    fun addAll(folderConfigs: List<FolderConfig>) = folderConfigs.forEach { addFolderConfig(it) }
}
