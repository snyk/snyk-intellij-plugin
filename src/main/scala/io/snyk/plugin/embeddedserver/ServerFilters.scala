package io.snyk.plugin.embeddedserver

trait ServerFilters { self: MiniServer =>

  /**
    * Exposes `redirectTo` in the form of a `Processor`,
    * avoids needing to write `_ => _ => redirectTo(...)` in filter implementations
    */
  private def redirectToProcessor(url: String): Processor = _ => _ => redirectTo(url)

  def requireProjectId(proc: Processor): Processor = {
    pluginState.safeProjectId match {
      case Some(_) => proc
      case None => redirectToProcessor("/project-not-available")
    }
  }

  def requireAuth(proc: Processor): Processor =
    if(pluginState.credentials.get.isFailure) redirectToProcessor("/please-login")
    else proc

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
