package snyk

import com.intellij.openapi.diagnostic.logger
import snyk.PropertyLoader.PropertyKeys.ENVIRONMENT
import snyk.PropertyLoader.PropertyKeys.SENTRY_DSN
import java.io.IOException
import java.util.Properties

object PropertyLoader {
    private val LOG = logger<PropertyLoader>()
    private const val DEFAULT_PROPERTY_FILE = "application.properties"

    private val properties = Properties()

    init {
        try {
            properties.load(javaClass.classLoader.getResourceAsStream(DEFAULT_PROPERTY_FILE))
        } catch (e: IllegalArgumentException) {
            LOG.warn("Property file contains a malformed Unicode escape sequence", e)
        } catch (e: IOException) {
            LOG.warn("Could not load $DEFAULT_PROPERTY_FILE", e)
        }
    }

    /**
     * The plugin environment (default value is `DEVELOPMENT`)
     */
    val environment: String by lazy {
        val loadedValue = properties.getProperty(ENVIRONMENT)
        val defaultValue = "DEVELOPMENT"
        if (loadedValue == null) {
            logMissingProperty(ENVIRONMENT, defaultValue)
        }
        loadedValue ?: defaultValue
    }

    /**
     * The Sentry DSN
     */
    val sentryDsn: String by lazy {
        val loadedValue = properties.getProperty(SENTRY_DSN)
        val defaultValue = ""
        if (loadedValue == null) {
            logMissingProperty(SENTRY_DSN, defaultValue)
        }
        loadedValue ?: defaultValue
    }

    private fun logMissingProperty(value: String, defaultValue: String) {
        LOG.warn("Property '$value' was not found in $DEFAULT_PROPERTY_FILE, use '$defaultValue' as default value")
    }

    /**
     * Property keys from `src/main/resources/application.properties` file.
     */
    private object PropertyKeys {
        const val ENVIRONMENT = "environment"
        const val SENTRY_DSN = "sentry.dsn"
    }
}
