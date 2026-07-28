@file:JvmName("PluginInformationKt")

package snyk

import com.intellij.ide.plugins.cl.PluginAwareClassLoader
import com.intellij.openapi.application.ApplicationInfo

/** Snyk IntelliJ plugin information. */
val pluginInfo: PluginInformation by lazy { getPluginInformation() }

private fun getPluginInformation(): PluginInformation {
  val classLoader = PluginInformation::class.java.classLoader
  val snykPluginVersion =
    (classLoader as? PluginAwareClassLoader)?.pluginDescriptor?.version ?: "UNKNOWN"

  val applicationInfo = ApplicationInfo.getInstance()
  val integrationEnvironment =
    when (val name = applicationInfo.versionName) {
      "IntelliJ IDEA",
      "PyCharm" -> "$name ${applicationInfo.apiVersion.substring(0, 2)}"
      else -> name
    }

  return PluginInformation(
    integrationName = "JETBRAINS_IDE",
    integrationVersion = snykPluginVersion,
    integrationEnvironment = integrationEnvironment.uppercase(),
    integrationEnvironmentVersion = applicationInfo.fullVersion,
  )
}

/**
 * Holds all relevant information for the Snyk plugin such version, integration name, environment
 * etc.
 */
data class PluginInformation(
  val integrationName: String,
  val integrationVersion: String,
  val integrationEnvironment: String,
  val integrationEnvironmentVersion: String,
)
