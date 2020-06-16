package io.snyk.plugin.client

import java.io.IOException
import java.nio.file.Paths
import java.util
import java.util.Locale

class Platform(val snykWrapperFileName: String)

object Platform {

  val Linux = new Platform("snyk-linux")
  val LinuxAlpine = new Platform("snyk-alpine")
  val MacOS = new Platform("snyk-macos")
  val Windows = new Platform("snyk-win.exe")

  @throws[PlatformDetectionException]
  def current: Platform = detect(System.getProperties)

  @throws[PlatformDetectionException]
  def detect(systemProperties: util.Map[AnyRef, AnyRef]) = {
    val architectureName = systemProperties.get("os.name").asInstanceOf[String].toLowerCase(Locale.ENGLISH)

    architectureName match {
      case "linux" => if (Paths.get("/etc/alpine-release").toFile.exists) LinuxAlpine else Linux
      case "mac os x" | "darwin" | "osx" => MacOS
      case name if name.contains("windows") => Windows
      case _ => throw new PlatformDetectionException(architectureName + " is not supported CPU type")
    }
  }
}

class PlatformDetectionException(val message: String) extends IOException(message)