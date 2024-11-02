
import model.{ModelParam, TransformerModel}
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork
import utils.{AppConfig, AppLogger, AppSpark}

object SparkAssigment {

  def main(args: Array[String]): Unit = {
    // specify the log
    val logger = AppLogger("SparkAssignment")

    if (args.length < 2) {
      logger.info("Cannot find the input and output argument!")
      return
    }
    logger.info("Start loading config")
    // Start load config
    val conf = AppConfig()
    logger.info("Load config success")

    logger.info("Load Spark Config")
    val sc = AppSpark.createSparkSession("Sliding Window Dataset with Position Embedding", true)

    // Start load data from Spark
    logger.info("Start load and parallelize data")
    val slidingWindowDataset = TextDataset.loadDataSpark(args(0), sc, conf.getInt("app.trainingParam.windowSize"))

    // Other way to load data from local
//    val sentences = TextDataset.preprocessData(dataset)
//    val distData = sc.sparkContext.parallelize(sentences)

//    val slidingWindowDataset = distData.flatMap(
//        sentence => createSlidingWindowWithPositionalEmbedding(sentence.split(" ").toList, conf.getInt("app.trainingParam.windowSize")
//      ).iterator)

    // Save dataset to files
//    logger.info("Start computing dataset and when finish save it to files")
//    val datasetPath = TextDataset.saveToFiles(slidingWindowDataset, conf.getConfig("app.dataset"), sc.sparkContext)

    logger.info("Define Model using Deeplearning4J with Spark")
    val model : MultiLayerNetwork = TransformerModel.fromParam(new ModelParam())  // Input size, hidden size, ou
    // Load or create a model (assuming the model has been defined, e.g., TransformerModel.createModel())
    model.init()
//    testModel(model)

    SparkTraining.train(sc, model, slidingWindowDataset, conf, args(1))
    logger.info("Training complete.")
  }
}
    // main function

  
