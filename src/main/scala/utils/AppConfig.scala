package utils

import java.util
import com.typesafe.config.{Config, ConfigFactory}

object AppConfig {
  val conf: Config = ConfigFactory.load()
  def apply() : Config = conf
  def getListTask: util.List[? <: Config] = conf.getConfigList("app.task")
  def getTrainingParam: Config = conf.getConfig("app.trainingParam")
  def getEmbeddingModel: Config = conf.getConfig("app.modelParam")
}
