
import model.{ModelParam, TransformerModel}
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork
import utils.{AppConfig, AppLogger, AppSpark}

object SparkAssignment {

  def main(args: Array[String]): Unit = {
    // specify the log
    val logger = AppLogger("SparkAssignment")

    if (args.length < 2) {
      logger.error("Cannot find the input and output argument!")
      return
    }
    logger.warn("Start loading config")
    // Start load config
    val conf = AppConfig()
    logger.warn("Load config success")

    logger.warn("Load Spark Config")
    val sc = AppSpark.createSparkSession("Sliding Window Dataset with Position Embedding", true)
    sc.sparkContext.setLogLevel("WARN")

    // Start load data from Spark
    logger.warn("Start load and parallelize data")
    val slidingWindowDataset = TextDataset.loadDataSpark(args(0), sc, conf.getInt("app.trainingParam.windowSize"))
//    val slidingWindowDatasetPath = TextDataset.loadAndSaveData(args(0), sc, conf)

    logger.warn("Define Model using Deeplearning4J with Spark")
    // Load or create a model
    val model : MultiLayerNetwork = TransformerModel.fromParam(new ModelParam())  // Input size, hidden size, ou
    model.init()
//    testModel(model)

    SparkTraining.train(sc, model, slidingWindowDataset, conf, args(1))
    logger.warn("Training complete!")
  }
}
  
