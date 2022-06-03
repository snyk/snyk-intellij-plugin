package io.snyk.plugin.events

import com.intellij.util.messages.Topic

interface SnykProductsOrSeverityListener {
    companion object {
        val SNYK_ENABLEMENT_TOPIC =
            Topic.create(
                "Snyk Product's or Severity's enablement changed",
                SnykProductsOrSeverityListener::class.java
            )
    }

    fun enablementChanged()

}
