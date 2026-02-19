package io.snyk.plugin.events

import com.intellij.util.messages.Topic

interface SnykResultsFilteringListener {
  companion object {
    val SNYK_FILTERING_TOPIC =
      Topic.create("Snyk results filtering", SnykResultsFilteringListener::class.java)
  }

  fun filtersChanged()
}
