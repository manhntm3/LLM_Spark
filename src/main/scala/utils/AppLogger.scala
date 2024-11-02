package utils

import org.slf4j.{Logger, LoggerFactory}

//case class AppLogger(logger: Logger)
//  def apply() : Logger = logger

object AppLogger {
  def apply(name: String): Logger = {
    val logger = LoggerFactory.getLogger(name)
    Option(getClass.getClassLoader.getResourceAsStream("logback.xml")) match {
      case None => logger.error("Failed to locate logback.xml")
      case Some(inStream) =>
        try {
        } finally {
          inStream.close()
        }
    }
    logger
  }
}