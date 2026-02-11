package io.snyk.plugin.extensions

/**
 * SnykControllerManager is the extension point interface which other plugins can implement in order
 * to integrate with Snyk.
 */
interface SnykControllerManager {

  /**
   * register is called by the Snyk IntelliJ plugin to pass the #SnykController to extension point
   * implementers.
   */
  fun register(controller: SnykController)
}
