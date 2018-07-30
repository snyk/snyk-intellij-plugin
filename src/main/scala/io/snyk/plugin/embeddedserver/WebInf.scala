package io.snyk.plugin.embeddedserver

import java.nio.file.Paths
import java.net.{URL, URLConnection}


sealed trait WebInf {
  def openConnection(path: String): URLConnection
}

class FileBasedWebInf(root: String) extends WebInf {
  println(s"Serving WEB-INF from files: $root")
  def resolvePath(path: String): String =
    if(path.startsWith("/")) s"$root$path" else s"$root/$path"

  def openConnection(path: String): URLConnection =
    Paths.get(root, path).toUri.toURL.openConnection()
}

class JarBasedWebInf extends WebInf {
  println(s"Serving WEB-INF from classpath")
  def openConnection(path: String): URLConnection =
    getClass.getClassLoader.getResource(s"WEB-INF/$path").openConnection()
}

object WebInf {

  /**
  ** For UI testing purposes, detect if we're running as expanded
  ** files (e.g. _not_ in a jar).  If so, we must be in some sort of
  ** development environment, which means either a gradle or IntelliJ task.
  **
  ** Given this, we then rewrite the path specifically to replace known
  ** IntelliJ and Gradle build directories with the original source.
  **
  ** This then allow changes to src WEB-INF files to be picked up instantly
  ** by applications running in test/debug mode, which is especially important
  ** for rapid prototyping of UI changes without having to go through a full
  ** cycle of re-building and re-launching a new IntelliJ instance.
  **/
  lazy val instance: WebInf = {
    val root: URL = getClass.getClassLoader.getResource("WEB-INF")
    if (root.getProtocol == "file") {
      new FileBasedWebInf(root.getFile
        .replace("out/production", "src/main") //intelliJ
        .replace("build/resources/main", "src/main/resources") //gradle
      )
    } else new JarBasedWebInf
  }
}
