@file:JvmName("PluginInformationKt")

package snyk

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.extensions.PluginId

const val PLUGIN_ID = "io.snyk.snyk-intellij-plugin"

/**
 * Snyk IntelliJ plugin information.
 */
val pluginInfo: PluginInformation by lazy {
    getPluginInformation()
}

private fun getPluginInformation(): PluginInformation {
    val snykPluginVersion = PluginManagerCore.getPlugin(PluginId.getId(PLUGIN_ID))?.version ?: "UNKNOWN"

    val applicationInfo = ApplicationInfo.getInstance()
    val integrationEnvironment = when (val name = applicationInfo.versionName) {
        "IntelliJ IDEA", "PyCharm" -> "$name ${applicationInfo.apiVersion.substring(0, 2)}"
        else -> name
    }

    return PluginInformation(
        integrationName = "JETBRAINS_IDE",
        integrationVersion = snykPluginVersion,
        integrationEnvironment = integrationEnvironment.uppercase(),
        integrationEnvironmentVersion = applicationInfo.fullVersion
    )
}

/**
 * Holds all relevant information for the Snyk plugin such version, integration name, environment etc.
 */
data class PluginInformation(
    val integrationName: String,
    val integrationVersion: String,
    val integrationEnvironment: String,
    val integrationEnvironmentVersion: String
)
