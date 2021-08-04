package snyk.amplitude

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import snyk.amplitude.api.AmplitudeExperimentApiClient
import snyk.amplitude.api.AmplitudeExperimentApiClient.Defaults.FALLBACK_VARIANT
import snyk.amplitude.api.ExperimentUser
import snyk.amplitude.api.Variant
import java.io.IOException
import java.util.Properties
import java.util.concurrent.ConcurrentHashMap

private val LOG = logger<AmplitudeExperimentService>()

@Service
class AmplitudeExperimentService : Disposable {
    private var apiClient: AmplitudeExperimentApiClient? = null
    private val storage: ConcurrentHashMap<String, Variant> = ConcurrentHashMap()
    private var user: ExperimentUser = ExperimentUser("")

    init {
        try {
            val prop = Properties()
            prop.load(javaClass.classLoader.getResourceAsStream("application.properties"))
            val apiKey = prop.getProperty("amplitude.experiment.api-key") ?: ""
            apiClient = AmplitudeExperimentApiClient.create(apiKey = apiKey)
        } catch (e: IllegalArgumentException) {
            LOG.warn("Property file contains a malformed Unicode escape sequence", e)
        } catch (e: IOException) {
            LOG.warn("Could not load Amplitude Experiment API key", e)
        }
    }

    override fun dispose() {
        LOG.debug("Cleanup variant storage")
        storage.clear()
    }

    fun fetch(user: ExperimentUser) {
        if (apiClient == null) {
            LOG.warn("Amplitude experiment was not initialized, no results will be fetched for $user")
            return
        }

        this.user = user
        val variants = this.apiClient?.allVariants(this.user) ?: emptyMap()
        storeVariants(variants)
    }

    fun variant(key: String): Variant {
        return storage[key] ?: FALLBACK_VARIANT
    }

    private fun storeVariants(variants: Map<String, Variant>) {
        storage.clear()
        variants.forEach { (key, variant) ->
            storage[key] = variant
        }
        LOG.debug("Stored variants: $variants")
    }

    fun isShowScanningReminderEnabled(): Boolean {
        val variant = storage["intellij-show-scanning-reminder"] ?: return false
        return variant.value == "test"
    }
}
