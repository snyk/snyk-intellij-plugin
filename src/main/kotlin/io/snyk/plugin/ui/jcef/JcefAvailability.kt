package io.snyk.plugin.ui.jcef

import com.intellij.openapi.diagnostic.logger

/** Why the IDE can or cannot give this plugin an embedded browser. */
enum class JcefStatus {
  /** The embedded browser is reachable and the runtime can host it. */
  AVAILABLE,

  /**
   * The embedded browser classes are not reachable from this plugin's classloader.
   *
   * This is the IntelliJ 2026.2 case: JCEF moved out of the core platform into the separately
   * bundled "Web Browser (JCEF)" plugin (`com.intellij.modules.jcef`). It is visible only to
   * plugins that declare a dependency on it, and only when that bundled plugin actually resolved
   * for this OS and architecture.
   */
  CLASSES_MISSING,

  /** The classes are reachable, but the runtime cannot host a browser (e.g. a non-JBR runtime). */
  RUNTIME_UNSUPPORTED,
}

/**
 * The single seam through which embedded-browser (JCEF) availability is decided.
 *
 * This object must never reference a `com.intellij.ui.jcef` type — not in a signature, a field, a
 * body, or a catch clause. That is why `JBCefApp.isSupported()` cannot be the guard: it lives in
 * the very package that goes missing, so calling it throws the exact error it exists to prevent.
 * The one call that does need a JCEF type is isolated in [JcefSupportProbe], which is only ever
 * reached after the classes have been confirmed loadable.
 */
object JcefAvailability {
  private val logger = logger<JcefAvailability>()

  private const val PROBE_CLASS = "com.intellij.ui.jcef.JBCefApp"

  private val memoisedStatus: JcefStatus by lazy { probe() }

  fun status(): JcefStatus = memoisedStatus

  fun isAvailable(): Boolean = status() == JcefStatus.AVAILABLE

  /** Human-readable explanation of why the browser is unavailable, or null when it is available. */
  fun unavailableReason(): String? =
    when (status()) {
      JcefStatus.AVAILABLE -> null
      JcefStatus.CLASSES_MISSING ->
        "This IDE did not provide its embedded browser component to the Snyk plugin. " +
          "Since IntelliJ 2026.2 the embedded browser ships as the bundled " +
          "\"Web Browser (JCEF)\" plugin, which must be installed and enabled."
      JcefStatus.RUNTIME_UNSUPPORTED ->
        "This IDE runtime cannot host an embedded browser. " +
          "Run your IDE on a JetBrains Runtime (JBR) build to enable it."
    }

  private fun probe(): JcefStatus {
    val classesReachable =
      try {
        // Deliberately not initialising the class: we only need to know it resolves.
        Class.forName(PROBE_CLASS, false, JcefAvailability::class.java.classLoader)
        true
      } catch (t: Throwable) {
        // The real failure mode is NoClassDefFoundError, which is an Error and not an Exception,
        // so Throwable is the only catch that actually covers it.
        logger.info("Embedded browser classes are not reachable: ${t.javaClass.name}: ${t.message}")
        false
      }

    if (!classesReachable) return JcefStatus.CLASSES_MISSING

    return try {
      if (JcefSupportProbe.isSupported()) JcefStatus.AVAILABLE else JcefStatus.RUNTIME_UNSUPPORTED
    } catch (t: Throwable) {
      logger.warn("Embedded browser support check failed unexpectedly", t)
      JcefStatus.RUNTIME_UNSUPPORTED
    }
  }
}

/**
 * Isolates the single call that genuinely needs a JCEF type, so that [JcefAvailability] itself
 * stays free of them. Compiled to its own class file and only ever invoked once the classes have
 * been confirmed loadable, so resolving it cannot fail on the path where JCEF is absent.
 */
internal object JcefSupportProbe {
  fun isSupported(): Boolean = com.intellij.ui.jcef.JBCefApp.isSupported()
}
