import com.intellij.openapi.diagnostic.{Log4jBasedLogger, Logger}

object Log4jLoggerFactory {
  def install(): Unit = {
    Logger.setFactory(classOf[Log4jLoggerFactory])
  }
}

class Log4jLoggerFactory extends Logger.Factory {
  override def getLoggerInstance(category: String): Logger =
    new Log4jBasedLogger(
      org.apache.log4j.Logger.getLogger(category)
    )
}
