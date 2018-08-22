package io.snyk.plugin

import com.intellij.openapi.diagnostic.Logger

object IntellijLogging {
  class ScalaLogger(val delegate: Logger) extends AnyVal {

    def trace(message: => String): Unit = if(delegate.isTraceEnabled) delegate.trace(message)
    def trace(t: => Throwable)(implicit dummy: Int = 0): Unit = if(delegate.isTraceEnabled) delegate.trace(t)

    def debug(message: => String): Unit = if(delegate.isDebugEnabled) delegate.debug(message)
    def debug(message: => String, t: => Throwable): Unit = if(delegate.isDebugEnabled) delegate.debug(message, t)
    def debug(t: => Throwable)(implicit dummy: Int = 0): Unit = if(delegate.isDebugEnabled) delegate.debug(t)

    def info(message: String): Unit = delegate.info(message)
    def info(message: String, t: Throwable): Unit = delegate.info(message, t)
    def info(t: Throwable): Unit = delegate.info(t)

    def warn(message: String): Unit = delegate.warn(message)
    def warn(message: String, t: => Throwable): Unit = delegate.warn(message, t)
    def warn(t: Throwable): Unit = delegate.warn(t)

    def error(message: String): Unit = delegate.error(message)
    def error(message: String, t: => Throwable): Unit = delegate.error(message, t)
    def error(t: Throwable): Unit = delegate.error(t)
  }
}
trait IntellijLogging {
  import IntellijLogging._

  protected lazy val log: ScalaLogger = new ScalaLogger(Logger.getInstance(this.getClass))
}
