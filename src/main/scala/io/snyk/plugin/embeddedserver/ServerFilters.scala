package io.snyk.plugin.embeddedserver

import fi.iki.elonen.NanoHTTPD.Response

import scala.util.{Failure, Success}

trait ServerFilters { self: MiniServer =>

  log.debug(s"ServerFilters is here")

  def requireProjectId(fn: => Response): Response = {
    pluginState.safeProjectId match {
      case Some(p) =>
        log.debug(s"safe project ID was $p")
        fn
      case None =>
        log.info(s"no project available")
        redirectTo("/no-project-available")
    }
  }

  def requireAuth(fn: => Response): Response = {
        pluginState.credentials.get match {
      case Failure(ex) =>
        log.debug(s"Auth failed with $ex")
        redirectTo("/login-required")
      case Success(_) =>
        log.trace(s"We have auth")
        fn
    }
  }

  def requireScan(path: String, params: ParamSet)(fn: => Response): Response = {
    pluginState.latestScanForSelectedProject match {
      case Some(_) =>
        log.info("Reusing cached scan for project")
        fn
      case None =>
        log.info("Scan required for project")
        asyncScanAndRedirectTo(path, params)
        redirectTo("/scanning")
    }
  }
}
