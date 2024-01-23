package io.snyk.plugin.extensions

/**
 * SnykController is used by third-party plugins to interact with the Snyk plugin.
 */
interface SnykController {

    /**
     * scan enqueues a scan of the project for vulnerabilities.
     */
    fun scan()

    /**
     * userId returns the current authenticated Snyk user's ID.
     *
     * If no user is authenticated, this will return null.
     */
    fun userId(): String?
}
