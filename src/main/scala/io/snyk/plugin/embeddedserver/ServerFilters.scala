package io.snyk.plugin.embeddedserver

import scala.util.{Failure, Success}

trait ServerFilters { self: MiniServer =>

  def requireProjectId(proc: Processor): Processor = {
    url => {
      params => {
        pluginState.safeProjectId match {
          case Some(_) => proc(url)(params)
          case None => redirectTo("/no-project-available")
        }
      }
    }
  }

  def requireAuth(proc: Processor): Processor = {
    url => {
      params => {
        println("Auth test")
        pluginState.credentials.get match {
          case Failure(ex) =>
            println(s"Auth failed with $ex")
            redirectTo("/login-required")
          case Success(_) =>
            proc(url)(params)
        }
      }
    }
  }

  def requireScan(proc: Processor): Processor = {
    url => {
      params => {
        pluginState.latestScanForSelectedProject match {
          case Some(_) =>
            proc(url)(params)
          case None =>
            asyncScanAndRedirectTo(
              successPath = url,
              failurePath = url,
              params = params
            )
            redirectTo("/scanning")
        }
      }
    }
  }
}
