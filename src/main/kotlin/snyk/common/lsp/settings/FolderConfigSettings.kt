package snyk.common.lsp.settings

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import io.snyk.plugin.getContentRootPaths
import org.jetbrains.annotations.NotNull
import snyk.common.lsp.FolderConfig
import java.util.concurrent.ConcurrentHashMap
import java.util.stream.Collectors

@Suppress("UselessCallOnCollection")
@Service
class FolderConfigSettings {
    private val configs : MutableMap<String, FolderConfig> = ConcurrentHashMap<String, FolderConfig>()

    @Suppress("UselessCallOnNotNull", "USELESS_ELVIS", "UNNECESSARY_SAFE_CALL", "RedundantSuppression")
    fun addFolderConfig(@NotNull folderConfig: FolderConfig) {
        if (folderConfig?.folderPath.isNullOrBlank() ?: true) return
        configs[folderConfig.folderPath] = folderConfig
    }

    internal fun getFolderConfig(folderPath: String): FolderConfig {
        return configs[folderPath]!!
    }

    fun getAll(): Map<String, FolderConfig> {
        return HashMap(configs)
    }

    fun clear() = configs.clear()

    fun addAll(folderConfigs: List<FolderConfig>) = folderConfigs.mapNotNull { addFolderConfig(it) }

    fun getAllForProject(project: Project): List<FolderConfig> =
        project.getContentRootPaths()
            .mapNotNull { getFolderConfig(it.toAbsolutePath().toString()) }
            .filterNotNull()
            .stream()
            .sorted()
            .collect(Collectors.toList()).toList()
}
