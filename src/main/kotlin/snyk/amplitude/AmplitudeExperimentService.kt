package snyk.amplitude

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import snyk.amplitude.api.AmplitudeExperimentApiClient
import snyk.amplitude.api.AmplitudeExperimentApiClient.Defaults.FALLBACK_VARIANT
import snyk.amplitude.api.ExperimentUser
import snyk.amplitude.api.Variant
import java.util.concurrent.ConcurrentHashMap

@Service
class AmplitudeExperimentService : Disposable {
    private val log = logger<AmplitudeExperimentService>()

    private val apiClient: AmplitudeExperimentApiClient =
        AmplitudeExperimentApiClient.create(apiKey = "client-bQAwhmzLhgpgqAhKAqRzIjnS915YrAqj")
    private val storage: ConcurrentHashMap<String, Variant> = ConcurrentHashMap()
    private var user: ExperimentUser = ExperimentUser("")

    override fun dispose() {
        log.debug("Cleanup variant storage")
        storage.clear()
    }

    fun fetch(user: ExperimentUser) {
        this.user = user

        val variants = this.apiClient.allVariants(this.user)
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
        log.debug("Stored variants: $variants")
    }
}
