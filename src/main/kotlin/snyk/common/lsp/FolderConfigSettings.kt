package snyk.common.lsp

import com.google.gson.Gson
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.annotations.MapAnnotation
import io.snyk.plugin.getContentRootPaths
import java.util.stream.Collectors

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

    fun addFolderConfig(folderConfig: FolderConfig) {
        state.configs[folderConfig.folderPath] = gson.toJson(folderConfig)
    }

    @Suppress("USELESS_ELVIS", "SENSELESS_COMPARISON") // gson doesn't care about kotlin not null stuff
    internal fun getFolderConfig(folderPath: String): FolderConfig? {
        val fromJson = gson.fromJson(state.configs[folderPath], FolderConfig::class.java) ?: return null
        if (fromJson.additionalParameters == null) {
            val copy = fromJson.copy(
                baseBranch = fromJson.baseBranch,
                folderPath = fromJson.folderPath,
                localBranches = fromJson.localBranches ?: emptyList(),
                additionalParameters = fromJson.additionalParameters ?: emptyList(),
            )
            addFolderConfig(copy)
            return copy
        }
        return fromJson
    }

    fun getAll(): Map<String, FolderConfig> {
        return state.configs.map {
            it.key to gson.fromJson(it.value, FolderConfig::class.java)
        }.toMap()
    }

    fun clear() = state.configs.clear()

    fun addAll(folderConfigs: List<FolderConfig>) = folderConfigs.forEach { addFolderConfig(it) }

    fun getAllForProject(project: Project): List<FolderConfig> =
        project.getContentRootPaths()
            .mapNotNull { getFolderConfig(it.toAbsolutePath().toString()) }
            .stream()
            .sorted()
            .collect(Collectors.toList()).toList()
}
