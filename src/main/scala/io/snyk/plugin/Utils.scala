package io.snyk.plugin

import java.net.URL

object Utils {
  def isUrlValid(url: String): Boolean = try {
    if (url.nonEmpty) {
      new URL(url).toURI

      true
    } else {
      true
    }
  } catch {
    case _: Throwable => false
  }
}
